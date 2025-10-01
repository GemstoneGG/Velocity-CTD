/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PreTransferEvent;
import com.velocitypowered.api.event.player.CookieRequestEvent;
import com.velocitypowered.api.event.player.CookieStoreEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerResourcePackRemoveEvent;
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.ClientConfigSessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.player.resourcepack.VelocityResourcePackInfo;
import com.velocitypowered.proxy.connection.player.resourcepack.handler.ResourcePackHandler;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintFrameDecoder;
import com.velocitypowered.proxy.protocol.packet.ClientboundCookieRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundStoreCookiePacket;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.RemoveResourcePackPacket;
import com.velocitypowered.proxy.protocol.packet.ResourcePackRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ResourcePackResponsePacket;
import com.velocitypowered.proxy.protocol.packet.TransferPacket;
import com.velocitypowered.proxy.protocol.packet.config.ClientboundCustomReportDetailsPacket;
import com.velocitypowered.proxy.protocol.packet.config.ClientboundServerLinksPacket;
import com.velocitypowered.proxy.protocol.packet.config.CodeOfConductPacket;
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket;
import com.velocitypowered.proxy.protocol.packet.config.RegistrySyncPacket;
import com.velocitypowered.proxy.protocol.packet.config.StartUpdatePacket;
import com.velocitypowered.proxy.protocol.packet.config.TagsUpdatePacket;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.key.Key;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A special session handler that catches "last minute" disconnects. This version is to accommodate
 * 1.20.2+ switching. Yes, some of this is exceptionally stupid.
 */
public class ConfigSessionHandler implements MinecraftSessionHandler {

  /**
   * The logger for reporting configuration session events and errors.
   */
  private static final Logger logger = LogManager.getLogger(ConfigSessionHandler.class);

  /**
   * The Velocity server instance.
   */
  private final VelocityServer server;

  /**
   * The server connection being configured.
   */
  private final VelocityServerConnection serverConn;

  /**
   * The future that will be completed when the connection result is known.
   */
  private final CompletableFuture<Impl> resultFuture;

  /**
   * A pending resource pack to reapply after configuration is complete.
   */
  private ResourcePackInfo resourcePackToApply;

  /**
   * The current state of the configuration session.
   */
  private final State state;

  /**
   * Creates the new transition handler.
   *
   * @param server       the Velocity server instance
   * @param serverConn   the server connection
   * @param resultFuture the result future
   */
  ConfigSessionHandler(final VelocityServer server, final VelocityServerConnection serverConn,
                       final CompletableFuture<Impl> resultFuture) {
    this.server = server;
    this.serverConn = serverConn;
    this.resultFuture = resultFuture;
    this.state = State.START;
  }

  /**
   * Called when this session handler is activated.
   *
   * <p>For Minecraft 1.20.2 clients, captures the currently applied resource pack (if any)
   * so it can be reapplied after the configuration phase completes.</p>
   */
  @Override
  public void activated() {
    ConnectedPlayer player = serverConn.getPlayer();
    if (player.getProtocolVersion() == ProtocolVersion.MINECRAFT_1_20_2) {
      resourcePackToApply = player.resourcePackHandler().getFirstAppliedPack();
      player.resourcePackHandler().clearAppliedResourcePacks();
    }
  }

  /**
   * Called before any packet is handled.
   *
   * <p>This checks whether the server connection is still active. If it is not, the connection
   * is immediately disconnected and packet processing is aborted.</p>
   *
   * @return {@code true} if the connection is no longer valid and should be closed
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
   * Handles a {@link StartUpdatePacket} by forwarding it to the backend server.
   *
   * @param packet the packet to forward
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final StartUpdatePacket packet) {
    serverConn.ensureConnected().write(packet);
    return true;
  }

  /**
   * Forwards a {@link TagsUpdatePacket} to the player client.
   *
   * @param packet the packet to forward
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final TagsUpdatePacket packet) {
    serverConn.getPlayer().getConnection().write(packet);
    return true;
  }

  /**
   * Forwards a {@link ClientboundCustomReportDetailsPacket} to the player client.
   *
   * @param packet the packet to forward
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final ClientboundCustomReportDetailsPacket packet) {
    serverConn.getPlayer().getConnection().write(packet);
    return true;
  }

  /**
   * Forwards a {@link ClientboundServerLinksPacket} to the player client.
   *
   * @param packet the packet to forward
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final ClientboundServerLinksPacket packet) {
    serverConn.getPlayer().getConnection().write(packet);
    return true;
  }

  /**
   * Handles a {@link KeepAlivePacket} by recording the timestamp and forwarding it to the client.
   *
   * @param packet the keep-alive packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final KeepAlivePacket packet) {
    serverConn.getPendingPings().put(packet.getRandomId(), System.nanoTime());
    serverConn.getPlayer().getConnection().write(packet);
    return true;
  }

  /**
   * Handles a {@link ResourcePackRequestPacket} from the backend by optionally queuing or skipping
   * the resource pack on the player client, based on plugin event results.
   *
   * @param packet the resource pack request
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final ResourcePackRequestPacket packet) {
    final MinecraftConnection playerConnection = serverConn.getPlayer().getConnection();

    final ResourcePackInfo resourcePackInfo = packet.toServerPromptedPack();
    final ServerResourcePackSendEvent event = new ServerResourcePackSendEvent(resourcePackInfo, this.serverConn);

    server.getEventManager().fire(event).thenAcceptAsync(serverResourcePackSendEvent -> {
      if (playerConnection.isClosed()) {
        return;
      }

      if (serverResourcePackSendEvent.getResult().isAllowed()) {
        final ResourcePackInfo toSend = serverResourcePackSendEvent.getProvidedResourcePack();
        boolean modifiedPack = false;
        if (toSend != serverResourcePackSendEvent.getReceivedResourcePack()) {
          ((VelocityResourcePackInfo) toSend).setOriginalOrigin(
              ResourcePackInfo.Origin.DOWNSTREAM_SERVER);
          modifiedPack = true;
        }

        if (serverConn.getPlayer().resourcePackHandler().hasPackAppliedByHash(toSend.getHash())) {
          // Do not apply a resource pack that has already been applied
          if (serverConn.getConnection() != null) {
            // We can technically skip these first 2 states, however, for conformity to normal state flow expectations...
            serverConn.getConnection().write(new ResourcePackResponsePacket(
                packet.getId(), packet.getHash(), PlayerResourcePackStatusEvent.Status.ACCEPTED));
            serverConn.getConnection().write(new ResourcePackResponsePacket(
                packet.getId(), packet.getHash(), PlayerResourcePackStatusEvent.Status.DOWNLOADED));
            serverConn.getConnection().write(new ResourcePackResponsePacket(
                packet.getId(), packet.getHash(), PlayerResourcePackStatusEvent.Status.SUCCESSFUL));
          }

          if (modifiedPack) {
            logger.warn("A plugin has tried to modify a ResourcePack provided by the backend server "
                    + "with a ResourcePack already applied, the applying of the resource pack will be skipped.");
          }
        } else {
          resourcePackToApply = null;
          serverConn.getPlayer().resourcePackHandler().queueResourcePack(toSend);
        }
      } else if (serverConn.getConnection() != null) {
        serverConn.getConnection().write(new ResourcePackResponsePacket(
                packet.getId(), packet.getHash(), PlayerResourcePackStatusEvent.Status.DECLINED));
      }
    }, playerConnection.eventLoop()).exceptionally((ex) -> {
      if (serverConn.getConnection() != null) {
        serverConn.getConnection().write(new ResourcePackResponsePacket(
                packet.getId(), packet.getHash(), PlayerResourcePackStatusEvent.Status.DECLINED));
      }
      logger.error("Exception while handling resource pack send for {}", playerConnection, ex);
      return null;
    });

    return true;
  }

  /**
   * Handles a {@link RemoveResourcePackPacket} by clearing or removing a specific pack
   * from the player’s resource pack handler.
   *
   * @param packet the remove resource pack packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final RemoveResourcePackPacket packet) {
    final MinecraftConnection playerConnection = this.serverConn.getPlayer().getConnection();

    final ServerResourcePackRemoveEvent event = new ServerResourcePackRemoveEvent(packet.getId(), this.serverConn);
    server.getEventManager().fire(event).thenAcceptAsync(serverResourcePackRemoveEvent -> {
      if (playerConnection.isClosed()) {
        return;
      }

      if (serverResourcePackRemoveEvent.getResult().isAllowed()) {
        final ConnectedPlayer player = serverConn.getPlayer();
        final ResourcePackHandler handler = player.resourcePackHandler();
        if (packet.getId() != null) {
          handler.remove(packet.getId());
        } else {
          handler.clearAppliedResourcePacks();
        }
        playerConnection.write(packet);
      }
    }, playerConnection.eventLoop()).exceptionally((ex) -> {
      logger.error("Exception while handling resource pack remove for {}", playerConnection, ex);
      return null;
    });

    return true;
  }

  /**
   * Handles a {@link FinishedUpdatePacket} indicating that the backend has completed its configuration phase.
   *
   * <p>This method performs session handoff logic and finalizes player state.</p>
   *
   * @param packet the packet indicating config completion
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final FinishedUpdatePacket packet) {
    final MinecraftConnection smc = serverConn.ensureConnected();
    final ConnectedPlayer player = serverConn.getPlayer();
    final ClientConfigSessionHandler configHandler = (ClientConfigSessionHandler) player.getConnection().getActiveSessionHandler();

    smc.getChannel().pipeline().get(MinecraftVarintFrameDecoder.class).setState(StateRegistry.PLAY);
    smc.getChannel().pipeline().get(MinecraftDecoder.class).setState(StateRegistry.PLAY);
    // noinspection DataFlowIssue
    configHandler.handleBackendFinishUpdate(serverConn).thenRunAsync(() -> {
      smc.write(FinishedUpdatePacket.INSTANCE);
      if (serverConn == player.getConnectedServer()) {
        smc.setActiveSessionHandler(StateRegistry.PLAY);
        player.sendPlayerListHeaderAndFooter(player.getPlayerListHeader(), player.getPlayerListFooter());
        // The client cleared the tab list. TODO: Restore changes done via TabList API
        player.getTabList().clearAllSilent();
      } else {
        smc.setActiveSessionHandler(StateRegistry.PLAY, new TransitionSessionHandler(server, serverConn, resultFuture));
      }

      if (player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21)) {
        String target = serverConn.getServerInfo().getName();
        player.setServerLinks(server.getConfiguration().getServerLinksFor(target));
      }

      if (player.resourcePackHandler().getFirstAppliedPack() == null && resourcePackToApply != null) {
        player.resourcePackHandler().queueResourcePack(resourcePackToApply);
      }
    }, smc.eventLoop());
    return true;
  }

  /**
   * Handles a {@link DisconnectPacket} from the backend server during configuration.
   *
   * @param packet the disconnect packet
   * @return {@code true} if the disconnect was handled and session ended
   */
  @Override
  public boolean handle(final DisconnectPacket packet) {
    serverConn.disconnect();
    resultFuture.complete(ConnectionRequestResults.forDisconnect(packet, serverConn.getServer()));
    return true;
  }

  /**
   * Handles a {@link PluginMessagePacket} sent during the config phase.
   *
   * <p>If the message is a brand packet, it is rewritten. Otherwise, it is passed through
   * the plugin messaging system, firing a {@link PluginMessageEvent}.</p>
   *
   * @param packet the plugin message
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final PluginMessagePacket packet) {
    if (PluginMessageUtil.isMcBrand(packet)) {
      serverConn.getPlayer().getConnection().write(PluginMessageUtil.rewriteMinecraftBrand(packet,
          server.getVersion(),
          serverConn.getPlayer().getProtocolVersion(),
          server.getConfiguration().getServerBrand(),
          server.getConfiguration().getProxyBrandCustom(),
          server.getConfiguration().getBackendBrandCustom(),
          serverConn.getServer().getServerInfo().getName(),
          ProtocolVersion.getVersionByName(server.getConfiguration().getMinimumVersion()).getVersionIntroducedIn()));
    } else {
      byte[] bytes = ByteBufUtil.getBytes(packet.content());
      ChannelIdentifier id = this.server.getChannelRegistrar().getFromId(packet.getChannel());

      if (id == null) {
        serverConn.getPlayer().getConnection().write(packet.retain());
        return true;
      }

      // Handling this stuff async means that we should probably pause
      // the connection while we toss this off into another pool
      this.serverConn.getConnection().setAutoReading(false);
      this.server.getEventManager()
          .fire(new PluginMessageEvent(serverConn, serverConn.getPlayer(), id, bytes))
          .thenAcceptAsync(pme -> {
            if (pme.getResult().isAllowed() && !serverConn.getPlayer().getConnection().isClosed()) {
              serverConn.getPlayer().getConnection().write(new PluginMessagePacket(
                  pme.getIdentifier().getId(), Unpooled.wrappedBuffer(bytes)));
            }
            this.serverConn.getConnection().setAutoReading(true);
          }, serverConn.ensureConnected().eventLoop()).exceptionally((ex) -> {
            logger.error("Exception while handling plugin message {}", packet, ex);
            return null;
          });
    }

    return true;
  }

  /**
   * Handles a {@link RegistrySyncPacket} by forwarding it directly to the client.
   *
   * @param packet the registry sync packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final RegistrySyncPacket packet) {
    serverConn.getPlayer().getConnection().write(packet.retain());
    return true;
  }

  /**
   * Handles a {@link TransferPacket} during the configuration phase by optionally
   * forwarding the transfer request after firing a {@link PreTransferEvent}.
   *
   * @param packet the transfer packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final TransferPacket packet) {
    final InetSocketAddress originalAddress = packet.address();
    if (originalAddress == null) {
      logger.error("""
          Unexpected nullable address received in TransferPacket \
          from Backend Server in Configuration State""");
      return true;
    }

    this.server.getEventManager()
            .fire(new PreTransferEvent(this.serverConn.getPlayer(), originalAddress))
            .thenAcceptAsync(event -> {
              if (event.getResult().isAllowed()) {
                InetSocketAddress resultedAddress = event.getResult().address();
                if (resultedAddress == null) {
                  resultedAddress = originalAddress;
                }
                serverConn.getPlayer().getConnection().write(new TransferPacket(
                        resultedAddress.getHostName(), resultedAddress.getPort()));
              }
            }, serverConn.ensureConnected().eventLoop());
    return true;
  }

  /**
   * Handles a {@link ClientboundStoreCookiePacket} by firing a {@link CookieStoreEvent}
   * and forwarding the result if allowed.
   *
   * @param packet the cookie packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final ClientboundStoreCookiePacket packet) {
    server.getEventManager()
        .fire(new CookieStoreEvent(serverConn.getPlayer(), packet.getKey(), packet.getPayload()))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            final Key resultedKey = event.getResult().getKey() == null
                ? event.getOriginalKey() : event.getResult().getKey();
            final byte[] resultedData = event.getResult().getData() == null
                ? event.getOriginalData() : event.getResult().getData();

            serverConn.getPlayer().getConnection()
                .write(new ClientboundStoreCookiePacket(resultedKey, resultedData));
          }
        }, serverConn.ensureConnected().eventLoop());

    return true;
  }

  /**
   * Handles a {@link ClientboundCookieRequestPacket} by firing a {@link CookieRequestEvent}
   * and forwarding the request if permitted.
   *
   * @param packet the cookie request packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final ClientboundCookieRequestPacket packet) {
    server.getEventManager().fire(new CookieRequestEvent(serverConn.getPlayer(), packet.getKey()))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            final Key resultedKey = event.getResult().getKey() == null
                ? event.getOriginalKey() : event.getResult().getKey();
            serverConn.getPlayer().getConnection().write(new ClientboundCookieRequestPacket(resultedKey));
          }
        }, serverConn.ensureConnected().eventLoop());

    return true;
  }

  /**
   * Handles a {@link CodeOfConductPacket} by forwarding it to the player client.
   *
   * @param packet the code-of-conduct packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final CodeOfConductPacket packet) {
    this.serverConn.getPlayer().getConnection().write(packet.retain());
    return true;
  }

  /**
   * Called when the backend server connection is closed during configuration.
   *
   * <p>This shuts down the player's connection.</p>
   */
  @Override
  public void disconnected() {
    final ConnectedPlayer player = serverConn.getPlayer();
    player.teardown();
  }

  /**
   * Handles any packet not explicitly defined by forwarding it to the client connection.
   *
   * @param packet the generic packet
   */
  @Override
  public void handleGeneric(final MinecraftPacket packet) {
    serverConn.getPlayer().getConnection().write(packet);
  }

  private void switchFailure(final Throwable cause) {
    logger.error("Unable to switch to new server {} for {}", serverConn.getServerInfo().getName(),
        serverConn.getPlayer().getUsername(), cause);
    serverConn.getPlayer().disconnect(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
    resultFuture.completeExceptionally(cause);
  }

  /**
   * Gets the current state of the configuration session.
   *
   * @return the current {@link State} of this configuration handler
   */
  public State getState() {
    return state;
  }

  /**
   * Represents the state of the configuration stage.
   */
  public enum State {

    /**
     * The initial state before any configuration-related packets have been handled.
     */
    START,

    /**
     * The state while the configuration process is actively negotiating capabilities
     * between the client and the backend server.
     */
    NEGOTIATING,

    /**
     * A plugin message (e.g., branding, mod channels) has interrupted the configuration flow,
     * pausing or deferring progress.
     */
    PLUGIN_MESSAGE_INTERRUPT,

    /**
     * A resource pack-related exchange (prompt, response, or removal) has interrupted
     * the normal configuration process.
     */
    RESOURCE_PACK_INTERRUPT,

    /**
     * The configuration stage has completed successfully, and the session is now ready for play.
     */
    COMPLETE
  }
}
