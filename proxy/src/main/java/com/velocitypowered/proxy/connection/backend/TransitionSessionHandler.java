/*
 * Copyright (C) 2018-2026 Velocity Contributors
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
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A special session handler that catches "last minute" disconnects.
 */
public class TransitionSessionHandler implements MinecraftSessionHandler {

  /**
   * Logger instance for reporting session handler events and errors.
   */
  private static final Logger LOGGER = LogManager.getLogger(TransitionSessionHandler.class);

  /**
   * The main Velocity server instance.
   */
  private final VelocityServer server;

  /**
   * The connection to the backend server being transitioned to.
   */
  private final VelocityServerConnection serverConn;

  /**
   * A future representing the result of the connection attempt.
   */
  private final CompletableFuture<Impl> resultFuture;

  /**
   * Handles BungeeCord-compatible plugin messaging during transition.
   */
  private final BungeeCordMessageResponder bungeecordMessageResponder;

  /**
   * Creates the new transition handler.
   *
   * @param server       the Velocity server instance
   * @param serverConn   the server connection
   * @param resultFuture the result future
   */
  TransitionSessionHandler(final VelocityServer server,
                           final VelocityServerConnection serverConn,
                           final CompletableFuture<Impl> resultFuture) {
    this.server = server;
    this.serverConn = serverConn;
    this.resultFuture = resultFuture;
    this.bungeecordMessageResponder = new BungeeCordMessageResponder(server, serverConn.getPlayer());
  }

  /**
   * Called before handling a packet in the transition phase.
   *
   * <p>If the backend server connection is no longer active, the session is disconnected and
   * the packet is not processed.</p>
   *
   * @return {@code true} if the connection is obsolete and should stop handling packets
   */
  @Override
  public boolean beforeHandle() {
    if (!serverConn.isActive()) {
      // Obsolete connection
      serverConn.disconnect();
      return true;
    }

    return false;
  }

  /**
   * Handles an inbound {@link KeepAlivePacket} during the transition phase.
   *
   * <p>The packet is forwarded to the backend server to keep the connection alive.</p>
   *
   * @param packet the keep-alive packet
   * @return {@code true} if the packet was forwarded
   */
  @Override
  public boolean handle(final KeepAlivePacket packet) {
    serverConn.ensureConnected().write(packet);
    return true;
  }

  /**
   * Handles a {@link JoinGamePacket} during the transition phase.
   *
   * <p>This finalizes the connection switch by applying the new session handler, updating player
   * state, and completing the result future.</p>
   *
   * @param packet the join game packet
   * @return {@code true} if the transition was completed
   */
  @Override
  public boolean handle(final JoinGamePacket packet) {
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

    // The goods are in hand! We got JoinGame. Let's transition completely to the new state.
    smc.setAutoReading(false);
    server.getEventManager()
        .fire(new ServerConnectedEvent(player, serverConn.getServer(), previousServer))
        .thenRunAsync(() -> {
          // Make sure we can still transition (player might have disconnected here).
          if (!serverConn.isActive()) {
            // Connection is obsolete.
            serverConn.disconnect();
            return;
          }

          // Change the client to use the ClientPlaySessionHandler if required.
          ClientPlaySessionHandler playHandler;
          if (player.getConnection().getActiveSessionHandler() instanceof ClientPlaySessionHandler sessionHandler) {
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

          // Clean up disabling auto-read while the connected event was being processed.
          // Do this after setting the connection, so no incoming packets are processed before
          // the API knows which server the player is connected to.
          smc.setAutoReading(true);

          // Send client settings. In 1.20.2+ this is done in the config state.
          if (smc.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_2)
              && player.getClientSettingsPacket() != null) {
            serverConn.ensureConnected().write(player.getClientSettingsPacket());
          }

          server.getClusterPlayerService().onPlayerSwitchServer(player,
              serverConn.getServerInfo().getName());

          if (this.server.isQueueEnabled()) {
            final VelocityQueue queue = this.server.getQueueManager().getQueue(serverConn.getServer()
                    .getServerInfo().getName());
            queue.dequeue(player.getUniqueId());
          }

          // We're done! :)
          server.getEventManager().fireAndForget(new ServerPostConnectEvent(player, previousServer));
          resultFuture.complete(ConnectionRequestResults.successful(serverConn.getServer()));
        }, smc.eventLoop()).exceptionally(exc -> {
          LOGGER.error("Unable to switch to new server {} for {}",
              serverConn.getServerInfo().getName(),
              player.getUsername(), exc);
          player.disconnect(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
          resultFuture.completeExceptionally(exc);
          return null;
        });

    return true;
  }

  /**
   * Handles a {@link DisconnectPacket} received from the backend during the transition.
   *
   * <p>If the backend is using Forge and the handshake is incomplete, the disconnect is treated as unsafe.
   * Otherwise, the disconnect is handled normally.</p>
   *
   * @param packet the disconnect packet
   * @return {@code true} after processing the disconnect
   */
  @Override
  public boolean handle(final DisconnectPacket packet) {
    final MinecraftConnection connection = serverConn.ensureConnected();
    serverConn.disconnect();

    // If we were in the middle of the Forge handshake, it is not safe to proceed. We must kick
    // the client.
    if (connection.getType() == ConnectionTypes.LEGACY_FORGE && !serverConn.getPhase().consideredComplete()) {
      resultFuture.complete(ConnectionRequestResults.forUnsafeDisconnect(packet, serverConn.getServer()));
    } else {
      resultFuture.complete(ConnectionRequestResults.forDisconnect(packet, serverConn.getServer()));
    }

    return true;
  }

  /**
   * Handles a {@link PluginMessagePacket} during the transition phase.
   *
   * <p>Plugin messages are used to support Forge handshakes, branding, and other transition state logic.
   * If the backend enters the {@code HELLO} phase, the previous server connection is marked as {@code IN_TRANSITION}.</p>
   *
   * @param packet the plugin message
   * @return {@code true} if the packet was processed or forwarded
   */
  @Override
  public boolean handle(final PluginMessagePacket packet) {
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
          existingConnection.getPhase().onDepartForNewServer(existingConnection, serverConn.getPlayer());
        }
      }

      return true;
    }

    serverConn.getPlayer().getConnection().write(packet.retain());
    return true;
  }

  /**
   * Called when the backend connection is closed unexpectedly during the transition phase.
   *
   * <p>Completes the result future with a {@link com.velocitypowered.api.proxy.ConnectionRequestBuilder.Status#SERVER_DISCONNECTED}
   * result so the connection attempt fails cleanly without throwing an exception. This leaves the
   * player tracked by the proxy and allows fallback handling (e.g. try-next) to proceed normally.
   * If the future is already complete (e.g. a {@link DisconnectPacket} was already handled),
   * this call is a no-op.</p>
   */
  @Override
  public void disconnected() {
    resultFuture.complete(ConnectionRequestResults.forDisconnect(
        ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR, serverConn.getServer()));
  }
}
