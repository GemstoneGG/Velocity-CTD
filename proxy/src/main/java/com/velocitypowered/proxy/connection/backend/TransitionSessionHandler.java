/*
 * Copyright (C) 2019-2023 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.connection.backend;

import static com.velocitypowered.proxy.connection.backend.BackendConnectionPhases.IN_TRANSITION;
import static com.velocitypowered.proxy.connection.forge.legacy.LegacyForgeHandshakeBackendPhase.HELLO;

import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.ConnectionTypes;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A special session handler that catches "last minute" disconnects.
 */
public class TransitionSessionHandler implements MinecraftSessionHandler {

  private static final Logger LOGGER = LogManager.getLogger(TransitionSessionHandler.class);

  private final VelocityServer server;
  private final VelocityServerConnection serverConn;
  private final CompletableFuture<Impl> resultFuture;
  private final BungeeCordMessageResponder bungeecordMessageResponder;

  // Backend packets that reach this handler before the switch completes, decoded or not, are held
  // and replayed after JoinGame: the client can only apply them once it has JoinGame, and dropping
  // one loses one-shot state such as the world clock sync (PaperMC/Velocity#1873). Reading is
  // paused while JoinGame is processed, so this is empty in the normal flow.
  private boolean joinGameProcessing;
  private final Queue<Object> deferredPackets = new ArrayDeque<>();

  /**
   * Creates the new transition handler.
   *
   * @param server       the Velocity server instance
   * @param serverConn   the server connection
   * @param resultFuture the result future
   */
  TransitionSessionHandler(VelocityServer server,
      VelocityServerConnection serverConn,
      CompletableFuture<Impl> resultFuture) {
    this.server = server;
    this.serverConn = serverConn;
    this.resultFuture = resultFuture;
    this.bungeecordMessageResponder = new BungeeCordMessageResponder(server,
        serverConn.getPlayer());
  }

  @Override
  public boolean beforeHandle() {
    if (!serverConn.isActive()) {
      // Obsolete connection
      serverConn.disconnect();
      return true;
    }
    return false;
  }

  @Override
  public boolean handle(KeepAlivePacket packet) {
    serverConn.ensureConnected().write(packet);
    return true;
  }

  @Override
  public boolean handle(JoinGamePacket packet) {
    // Hold packets that follow JoinGame until it's processed, then replay in order.
    this.joinGameProcessing = true;

    final MinecraftConnection smc = serverConn.ensureConnected();
    final VelocityRegisteredServer previousServer = serverConn.getPreviousServer().orElse(null);
    final ConnectedPlayer player = serverConn.getPlayer();
    final VelocityServerConnection existingConnection = player.getConnectedServer();

    if (existingConnection != null) {
      // Shut down the existing server connection.
      player.setConnectedServer(null);
      existingConnection.disconnect();

      // Send keep alive to try to avoid timeouts
      player.sendKeepAlive();
    }

    // Reset Tablist header and footer to prevent desync
    player.clearPlayerListHeaderAndFooter();

    // Override online mode
    packet.setOnlineMode(player.isOnlineMode());

    // The goods are in hand! We got JoinGame. Let's transition completely to the new state.
    smc.setAutoReading(false);
    server.getEventManager()
        .fire(new ServerConnectedEvent(player, serverConn.getServer(), previousServer))
        .thenRunAsync(() -> {
          // Make sure we can still transition (player might have disconnected here).
          if (!serverConn.isActive()) {
            // Connection is obsolete.
            releaseDeferredPackets();
            serverConn.disconnect();
            return;
          }

          // Change the client to use the ClientPlaySessionHandler if required.
          ClientPlaySessionHandler playHandler;
          if (player.getConnection()
              .getActiveSessionHandler() instanceof ClientPlaySessionHandler sessionHandler) {
            playHandler = sessionHandler;
          } else {
            playHandler = new ClientPlaySessionHandler(server, player);
            player.getConnection().setActiveSessionHandler(StateRegistry.PLAY, playHandler);
          }

          playHandler.handleBackendJoinGame(packet, serverConn);

          // Set the new play session handler for the server. We will have nothing more to do
          // with this connection once this task finishes up.
          smc.setActiveSessionHandler(StateRegistry.PLAY, new BackendPlaySessionHandler(server, serverConn));

          // Now set the connected server.
          serverConn.getPlayer().setConnectedServer(serverConn);

          // JoinGame processed: replay any packets held behind it before resuming reads.
          flushDeferredPackets();

          // Clean up disabling auto-read while the connected event was being processed.
          // Do this after setting the connection, so no incoming packets are processed before
          // the API knows which server the player is connected to.
          smc.setAutoReading(true);

          // Send client settings. In 1.20.2+ this is done in the config state.
          if (smc.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_2)
              && player.getClientSettingsPacket() != null) {
            serverConn.ensureConnected().write(player.getClientSettingsPacket());
          }

          server.getClusterPlayerService().onPlayerSwitchServer(
              player,
              previousServer != null ? previousServer.getServerInfo().getName() : null,
              serverConn.getServerInfo().getName());

          if (this.server.isQueueEnabled()) {
            VelocityQueue<?> queue = this.server.getQueueManager().getQueue(serverConn.getServer()
                    .getServerInfo().getName());
            queue.dequeue(player.getUniqueId());
          }

          // We're done! :)
          server.getEventManager().fireAndForget(new ServerPostConnectEvent(player,
              previousServer));
          resultFuture.complete(ConnectionRequestResults.successful(serverConn.getServer()));
        }, smc.eventLoop()).exceptionally(exc -> {
          LOGGER.error("Unable to switch to new server {} for {}",
              serverConn.getServerInfo().getName(),
              player.getUsername(), exc);
          releaseDeferredPackets();
          player.disconnect(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
          resultFuture.completeExceptionally(exc);
          return null;
        });

    return true;
  }

  @Override
  public boolean handle(DisconnectPacket packet) {
    final MinecraftConnection connection = serverConn.ensureConnected();
    serverConn.disconnect();

    // If we were in the middle of the Forge handshake, it is not safe to proceed. We must kick
    // the client.
    if (connection.getType() == ConnectionTypes.LEGACY_FORGE
        && !serverConn.getPhase().consideredComplete()) {
      resultFuture.complete(ConnectionRequestResults.forUnsafeDisconnect(packet,
          serverConn.getServer()));
    } else {
      resultFuture.complete(ConnectionRequestResults.forDisconnect(packet, serverConn.getServer()));
    }

    return true;
  }

  @Override
  public boolean handle(PluginMessagePacket packet) {
    // Hold plugin messages during JoinGame processing so they reach the client after it.
    if (joinGameProcessing) {
      ReferenceCountUtil.retain(packet);
      deferredPackets.add(packet);
      return true;
    }

    if (bungeecordMessageResponder.process(packet)) {
      return true;
    }

    // We always need to handle plugin messages, for Forge compatibility.
    if (serverConn.getPhase().handle(serverConn, serverConn.getPlayer(), packet)) {
      // Handled, but check the server connection phase.
      if (serverConn.getPhase() == HELLO) {
        VelocityServerConnection existingConnection = serverConn.getPlayer().getConnectedServer();
        if (existingConnection != null && existingConnection.getPhase() != IN_TRANSITION) {
          // Indicate that this connection is "in transition"
          existingConnection.setConnectionPhase(IN_TRANSITION);

          // Tell the player that we're leaving and we just aren't coming back.
          existingConnection.getPhase().onDepartForNewServer(existingConnection,
              serverConn.getPlayer());
        }
      }
      return true;
    }

    serverConn.getPlayer().getConnection().write(packet.retain());
    return true;
  }

  @Override
  public void handleGeneric(MinecraftPacket packet) {
    deferredPackets.add(ReferenceCountUtil.retain(packet));
  }

  @Override
  public void handleUnknown(ByteBuf buf) {
    deferredPackets.add(buf.retain());
  }

  private void flushDeferredPackets() {
    joinGameProcessing = false;
    if (deferredPackets.isEmpty()) {
      return;
    }

    final MinecraftConnection clientConn = serverConn.getPlayer().getConnection();
    Object packet;
    while ((packet = deferredPackets.poll()) != null) {
      clientConn.delayedWrite(packet);
    }
    clientConn.flush();
  }

  private void releaseDeferredPackets() {
    joinGameProcessing = false;
    Object packet;
    while ((packet = deferredPackets.poll()) != null) {
      ReferenceCountUtil.release(packet);
    }
  }

  @Override
  public void disconnected() {
    releaseDeferredPackets();
    resultFuture.complete(ConnectionRequestResults.forDisconnect(
        ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR, serverConn.getServer()));
  }
}
