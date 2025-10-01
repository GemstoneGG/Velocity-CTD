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

import static com.velocitypowered.proxy.connection.backend.BungeeCordMessageResponder.getBungeeCordChannel;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PreTransferEvent;
import com.velocitypowered.api.event.player.CookieRequestEvent;
import com.velocitypowered.api.event.player.CookieStoreEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerResourcePackRemoveEvent;
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.CommandGraphInjector;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.player.resourcepack.VelocityResourcePackInfo;
import com.velocitypowered.proxy.connection.player.resourcepack.handler.ResourcePackHandler;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintFrameDecoder;
import com.velocitypowered.proxy.protocol.packet.AvailableCommandsPacket;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import com.velocitypowered.proxy.protocol.packet.BundleDelimiterPacket;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundCookieRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundStoreCookiePacket;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.RemoveResourcePackPacket;
import com.velocitypowered.proxy.protocol.packet.ResourcePackRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ResourcePackResponsePacket;
import com.velocitypowered.proxy.protocol.packet.ServerDataPacket;
import com.velocitypowered.proxy.protocol.packet.TabCompleteResponsePacket;
import com.velocitypowered.proxy.protocol.packet.TransferPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.config.StartUpdatePacket;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.InetSocketAddress;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles a connected player.
 */
public class BackendPlaySessionHandler implements MinecraftSessionHandler {

  /**
   * Regex pattern used to validate plausible SHA1 hashes in resource packs.
   */
  private static final Pattern PLAUSIBLE_SHA1_HASH = Pattern.compile("^[a-z0-9]{40}$");

  /**
   * The logger instance for this session handler.
   */
  private static final Logger logger = LogManager.getLogger(BackendPlaySessionHandler.class);

  /**
   * Enables debug logging when backpressure prevents packet flushing.
   */
  private static final boolean BACKPRESSURE_LOG = Boolean.getBoolean("velocity.log-server-backpressure");

  /**
   * Maximum number of packets to flush before forcing a network flush.
   */
  private static final int MAXIMUM_PACKETS_TO_FLUSH = Integer.getInteger("velocity.max-packets-per-flush", 8192);

  /**
   * The main Velocity server instance.
   */
  private final VelocityServer server;

  /**
   * The connection to the backend server.
   */
  private final VelocityServerConnection serverConn;

  /**
   * The session handler for the player on the client side.
   */
  private final ClientPlaySessionHandler playerSessionHandler;

  /**
   * The Minecraft connection to the client.
   */
  private final MinecraftConnection playerConnection;

  /**
   * Handler for processing BungeeCord-compatible plugin messages.
   */
  private final BungeeCordMessageResponder bungeecordMessageResponder;

  /**
   * Whether an exception has been triggered during the session.
   */
  private boolean exceptionTriggered = false;

  /**
   * Number of packets flushed in the current batch.
   */
  private int packetsFlushed;

  BackendPlaySessionHandler(final VelocityServer server, final VelocityServerConnection serverConn) {
    this.server = server;
    this.serverConn = serverConn;
    this.playerConnection = serverConn.getPlayer().getConnection();

    MinecraftSessionHandler psh = playerConnection.getActiveSessionHandler();
    if (!(psh instanceof ClientPlaySessionHandler)) {
      throw new IllegalStateException(
          "Initializing BackendPlaySessionHandler with no backing client play session handler!");
    }

    this.playerSessionHandler = (ClientPlaySessionHandler) psh;

    this.bungeecordMessageResponder = new BungeeCordMessageResponder(server, serverConn.getPlayer());
  }

  /**
   * Called when this session handler is activated.
   *
   * <p>Registers the player with the backend server and sends the BungeeCord plugin channel
   * registration packet if BungeeCord plugin messaging is enabled in the configuration.</p>
   */
  @Override
  public void activated() {
    serverConn.getServer().addPlayer(serverConn.getPlayer());

    MinecraftConnection serverMc = serverConn.ensureConnected();
    if (server.getConfiguration().isBungeePluginChannelEnabled()) {
      serverMc.write(PluginMessageUtil.constructChannelsPacket(serverMc.getProtocolVersion(),
          ImmutableList.of(getBungeeCordChannel(serverMc.getProtocolVersion()))
      ));
    }
  }

  /**
   * Called before handling each inbound packet.
   *
   * <p>If the backend server connection is no longer active, the connection is closed and
   * packet handling is aborted.</p>
   *
   * @return {@code true} if the connection is obsolete and packet handling should be skipped
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
   * Handles a {@link BundleDelimiterPacket} by toggling the bundle state on the player's bundle handler.
   *
   * @param bundleDelimiterPacket the packet signaling a bundle boundary
   * @return {@code false} to allow the packet to continue being forwarded
   */
  @Override
  public boolean handle(final BundleDelimiterPacket bundleDelimiterPacket) {
    serverConn.getPlayer().getBundleHandler().toggleBundleSession();
    return false;
  }

  /**
   * Handles a {@link StartUpdatePacket} that transitions the player to the CONFIGURATION state.
   *
   * <p>Stops automatic reading, updates decoder state, and transitions the player’s client
   * session to configuration mode.</p>
   *
   * @param packet the update packet
   * @return {@code true} if handled successfully
   */
  @Override
  public boolean handle(final StartUpdatePacket packet) {
    MinecraftConnection smc = serverConn.ensureConnected();
    smc.setAutoReading(false);
    // Even when not auto reading messages are still decoded. Decode them with the correct state
    smc.getChannel().pipeline().get(MinecraftVarintFrameDecoder.class).setState(StateRegistry.CONFIG);
    smc.getChannel().pipeline().get(MinecraftDecoder.class).setState(StateRegistry.CONFIG);
    serverConn.getPlayer().switchToConfigState();
    return true;
  }

  /**
   * Handles a {@link KeepAlivePacket} by tracking the ping ID and timestamp for latency measurement.
   *
   * @param packet the keep-alive packet
   * @return {@code false} to continue forwarding the packet
   */
  @Override
  public boolean handle(final KeepAlivePacket packet) {
    serverConn.getPendingPings().put(packet.getRandomId(), System.nanoTime());
    return false; // forwards on
  }

  /**
   * Forwards a {@link ClientSettingsPacket} from the player to the backend server.
   *
   * @param packet the client settings
   * @return {@code true} if forwarded successfully
   */
  @Override
  public boolean handle(final ClientSettingsPacket packet) {
    serverConn.ensureConnected().write(packet);
    return true;
  }

  /**
   * Handles a {@link DisconnectPacket} by closing the connection and notifying the player
   * with the disconnect reason.
   *
   * @param packet the disconnect packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final DisconnectPacket packet) {
    serverConn.disconnect();
    serverConn.getPlayer().handleConnectionException(serverConn.getServer(), packet, true);
    return true;
  }

  /**
   * Processes a {@link BossBarPacket}, updating the player's server-side boss bar state.
   *
   * @param packet the boss bar packet
   * @return {@code false} to allow forwarding to the client
   */
  @Override
  public boolean handle(final BossBarPacket packet) {
    if (serverConn.getPlayer().getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
      if (packet.getAction() == BossBarPacket.ADD) {
        playerSessionHandler.getServerBossBars().add(packet.getUuid());
      } else if (packet.getAction() == BossBarPacket.REMOVE) {
        playerSessionHandler.getServerBossBars().remove(packet.getUuid());
      }
    }

    return false; // Forward
  }

  /**
   * Handles a {@link ResourcePackRequestPacket} sent by the backend.
   *
   * <p>This triggers a {@link ServerResourcePackSendEvent} and, depending on the result,
   * queues or skips the resource pack.</p>
   *
   * @param packet the resource pack request
   * @return {@code true} if the event was fired and processed
   */
  @Override
  public boolean handle(final ResourcePackRequestPacket packet) {
    final ResourcePackInfo.Builder builder = new VelocityResourcePackInfo.BuilderImpl(Preconditions.checkNotNull(packet.getUrl()))
        .setId(packet.getId())
        .setPrompt(packet.getPrompt() == null ? null : packet.getPrompt().getComponent())
        .setShouldForce(packet.isRequired())
        .setOrigin(ResourcePackInfo.Origin.DOWNSTREAM_SERVER);

    final String hash = packet.getHash();
    if (hash != null && !hash.isEmpty()) {
      if (PLAUSIBLE_SHA1_HASH.matcher(hash).matches()) {
        builder.setHash(ByteBufUtil.decodeHexDump(hash));
      }
    }

    final ResourcePackInfo resourcePackInfo = builder.build();
    final ServerResourcePackSendEvent event = new ServerResourcePackSendEvent(resourcePackInfo, this.serverConn);
    server.getEventManager().fire(event).thenAcceptAsync(serverResourcePackSendEvent -> {
      if (playerConnection.isClosed()) {
        return;
      }

      if (serverResourcePackSendEvent.getResult().isAllowed()) {
        final ResourcePackInfo toSend = serverResourcePackSendEvent.getProvidedResourcePack();
        boolean modifiedPack = false;
        if (toSend != serverResourcePackSendEvent.getReceivedResourcePack()) {
          ((VelocityResourcePackInfo) toSend)
              .setOriginalOrigin(ResourcePackInfo.Origin.DOWNSTREAM_SERVER);
          modifiedPack = true;
        }

        if (serverConn.getPlayer().resourcePackHandler().hasPackAppliedByHash(toSend.getHash())) {
          // Do not apply a resource pack that has already been applied
          if (serverConn.getConnection() != null) {
            serverConn.getConnection().write(new ResourcePackResponsePacket(
                    packet.getId(), packet.getHash(), PlayerResourcePackStatusEvent.Status.ACCEPTED));
            if (serverConn.getConnection().getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
              serverConn.getConnection().write(new ResourcePackResponsePacket(
                  packet.getId(), packet.getHash(),
                  PlayerResourcePackStatusEvent.Status.DOWNLOADED));
            }

            serverConn.getConnection().write(new ResourcePackResponsePacket(
                packet.getId(), packet.getHash(),
                PlayerResourcePackStatusEvent.Status.SUCCESSFUL));
          }

          if (modifiedPack) {
            logger.warn("A plugin has tried to modify a ResourcePack provided by the backend server "
                    + "with a ResourcePack already applied, the applying of the resource pack will be skipped.");
          }
        } else {
          serverConn.getPlayer().resourcePackHandler().queueResourcePack(toSend);
        }
      } else if (serverConn.getConnection() != null) {
        serverConn.getConnection().write(new ResourcePackResponsePacket(
            packet.getId(),
            packet.getHash(),
            PlayerResourcePackStatusEvent.Status.DECLINED
        ));
      }
    }, playerConnection.eventLoop()).exceptionally((ex) -> {
      if (serverConn.getConnection() != null) {
        serverConn.getConnection().write(new ResourcePackResponsePacket(
            packet.getId(),
            packet.getHash(),
            PlayerResourcePackStatusEvent.Status.DECLINED
        ));
      }

      logger.error("Exception while handling resource pack send for {}", playerConnection, ex);
      return null;
    });

    return true;
  }

  /**
   * Handles a {@link RemoveResourcePackPacket} by clearing or removing a resource pack from the player.
   *
   * @param packet the packet instructing a resource pack to be removed
   * @return {@code true} if handled
   */
  @Override
  public boolean handle(final RemoveResourcePackPacket packet) {
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
   * Handles a {@link PluginMessagePacket} from the backend server.
   *
   * <p>Processes BungeeCord plugin messages, rewrite brand messages, or fires a
   * {@link PluginMessageEvent} for custom channels.</p>
   *
   * @param packet the plugin message
   * @return {@code true} if the packet was handled or processed asynchronously
   */
  @Override
  public boolean handle(final PluginMessagePacket packet) {
    if (bungeecordMessageResponder.process(packet)) {
      return true;
    }

    // Register and unregister packets are simply forwarded to the server as-is.
    if (PluginMessageUtil.isRegister(packet) || PluginMessageUtil.isUnregister(packet)) {
      return false;
    }

    if (PluginMessageUtil.isMcBrand(packet)) {
      PluginMessagePacket rewritten = PluginMessageUtil.rewriteMinecraftBrand(packet,
          server.getVersion(),
          playerConnection.getProtocolVersion(),
          server.getConfiguration().getServerBrand(),
          server.getConfiguration().getProxyBrandCustom(),
          server.getConfiguration().getBackendBrandCustom(),
          serverConn.getServer().getServerInfo().getName(),
          ProtocolVersion.getVersionByName(server.getConfiguration().getMinimumVersion()).getVersionIntroducedIn());
      playerConnection.write(rewritten);
      return true;
    }

    if (serverConn.getPhase().handle(serverConn, serverConn.getPlayer(), packet)) {
      // Handled.
      return true;
    }

    ChannelIdentifier id = server.getChannelRegistrar().getFromId(packet.getChannel());
    if (id == null) {
      return false;
    }

    byte[] copy = ByteBufUtil.getBytes(packet.content());
    PluginMessageEvent event = new PluginMessageEvent(serverConn, serverConn.getPlayer(), id, copy);
    server.getEventManager().fire(event).thenAcceptAsync(pme -> {
      if (pme.getResult().isAllowed() && !playerConnection.isClosed()) {
        PluginMessagePacket copied = new PluginMessagePacket(
            packet.getChannel(), Unpooled.wrappedBuffer(copy));
        playerConnection.write(copied);
      }
    }, playerConnection.eventLoop()).exceptionally((ex) -> {
      logger.error("Exception while handling plugin message {}", packet, ex);
      return null;
    });

    return true;
  }

  /**
   * Handles a {@link TabCompleteResponsePacket} by passing it to the player's client session.
   *
   * @param packet the tab completion response
   * @return {@code true} if handled
   */
  @Override
  public boolean handle(final TabCompleteResponsePacket packet) {
    playerSessionHandler.handleTabCompleteResponse(packet);
    return true;
  }

  /**
   * Handles a legacy player list update and applies it to the player's tab list.
   *
   * @param packet the legacy tab list update
   * @return {@code false} to allow forwarding
   */
  @Override
  public boolean handle(final LegacyPlayerListItemPacket packet) {
    serverConn.getPlayer().getTabList().processLegacy(packet);
    return false;
  }

  /**
   * Applies a player info update to the tab list.
   *
   * @param packet the player info update
   * @return {@code false} to allow forwarding
   */
  @Override
  public boolean handle(final UpsertPlayerInfoPacket packet) {
    serverConn.getPlayer().getTabList().processUpdate(packet);
    return false;
  }

  /**
   * Applies a player info removal to the tab list.
   *
   * @param packet the player removal packet
   * @return {@code false} to allow forwarding
   */
  @Override
  public boolean handle(final RemovePlayerInfoPacket packet) {
    serverConn.getPlayer().getTabList().processRemove(packet);
    return false;
  }

  /**
   * Handles a {@link AvailableCommandsPacket}, injecting proxy commands and firing a
   * {@link PlayerAvailableCommandsEvent}.
   *
   * @param commands the available commands packet
   * @return {@code true} if handled successfully
   */
  @Override
  public boolean handle(final AvailableCommandsPacket commands) {
    RootCommandNode<CommandSource> rootNode = commands.getRootNode();
    if (server.getConfiguration().isAnnounceProxyCommands()) {
      // Inject commands from the proxy.
      final CommandGraphInjector<CommandSource> injector = server.getCommandManager().getInjector();
      injector.inject(rootNode, serverConn.getPlayer());

      // In 1.21.6 a confirmation prompt was added when executing a command via `run_command` click
      // action if the command is unknown. To prevent this prompt we have to send the command.
      if (this.playerConnection.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_21_6)) {
        rootNode.removeChildByName("velocity:callback");
      }
    }

    server.getEventManager().fire(new PlayerAvailableCommandsEvent(serverConn.getPlayer(), rootNode))
        .thenAcceptAsync(event -> playerConnection.write(commands), playerConnection.eventLoop())
        .exceptionally((ex) -> {
          logger.error("Exception while handling available commands for {}", playerConnection, ex);
          return null;
        });

    return true;
  }

  /**
   * Handles a {@link ServerDataPacket} containing server MOTD and ping metadata.
   *
   * <p>This fires a {@link ProxyPingEvent} and writes the resulting description back to the player.</p>
   *
   * @param packet the server data packet
   * @return {@code true} if handled
   */
  @Override
  public boolean handle(final ServerDataPacket packet) {
    server.getServerListPingHandler().getInitialPing(this.serverConn.getPlayer()).thenComposeAsync(ping -> server.getEventManager()
            .fire(new ProxyPingEvent(this.serverConn.getPlayer(), ping)),
        playerConnection.eventLoop()).thenAcceptAsync(pingEvent -> this.playerConnection.write(new ServerDataPacket(new ComponentHolder(
                this.serverConn.ensureConnected().getProtocolVersion(),
                pingEvent.getPing().getDescriptionComponent()),
                pingEvent.getPing().getFavicon().orElse(null), packet.isSecureChatEnforced())),
        playerConnection.eventLoop());
    return true;
  }

  /**
   * Handles a {@link TransferPacket} from the backend server.
   *
   * <p>This triggers a {@link PreTransferEvent} and rewrites the target address if allowed.</p>
   *
   * @param packet the transfer packet
   * @return {@code true} if handled
   */
  @Override
  public boolean handle(final TransferPacket packet) {
    final InetSocketAddress originalAddress = packet.address();
    if (originalAddress == null) {
      logger.error("""
          Unexpected nullable address received in TransferPacket \
          from Backend Server in Play State""");
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
            this.playerConnection.write(new TransferPacket(
                    resultedAddress.getHostName(), resultedAddress.getPort()));
          }
        }, playerConnection.eventLoop());

    return true;
  }

  /**
   * Handles a {@link ClientboundStoreCookiePacket} from the backend.
   *
   * <p>Fires a {@link CookieStoreEvent} and forwards the cookie if allowed.</p>
   *
   * @param packet the store cookie packet
   * @return {@code true} if handled
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

            playerConnection.write(new ClientboundStoreCookiePacket(resultedKey, resultedData));
          }
        }, playerConnection.eventLoop());

    return true;
  }

  /**
   * Handles a {@link ClientboundCookieRequestPacket} from the backend.
   *
   * <p>Fires a {@link CookieRequestEvent} and writes the request to the client if permitted.</p>
   *
   * @param packet the cookie request packet
   * @return {@code true} if handled
   */
  @Override
  public boolean handle(final ClientboundCookieRequestPacket packet) {
    server.getEventManager().fire(new CookieRequestEvent(serverConn.getPlayer(), packet.getKey()))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            final Key resultedKey = event.getResult().getKey() == null
                ? event.getOriginalKey() : event.getResult().getKey();

            playerConnection.write(new ClientboundCookieRequestPacket(resultedKey));
          }
        }, playerConnection.eventLoop());

    return true;
  }

  /**
   * Forwards all unrecognized packets to the client connection with buffering.
   *
   * @param packet the generic Minecraft packet
   */
  @Override
  public void handleGeneric(final MinecraftPacket packet) {
    if (packet instanceof PluginMessagePacket pluginMessage) {
      pluginMessage.retain();
    }

    playerConnection.delayedWrite(packet);
    if (++packetsFlushed >= MAXIMUM_PACKETS_TO_FLUSH) {
      playerConnection.flush();
      packetsFlushed = 0;
    }
  }

  /**
   * Forwards raw unhandled packet buffers to the client connection.
   *
   * @param buf the raw packet buffer
   */
  @Override
  public void handleUnknown(final ByteBuf buf) {
    playerConnection.delayedWrite(buf.retain());
    if (++packetsFlushed >= MAXIMUM_PACKETS_TO_FLUSH) {
      playerConnection.flush();
      packetsFlushed = 0;
    }
  }

  /**
   * Called after a full read cycle is completed.
   *
   * <p>This flushes any buffered packets to the client and resets the flush counter.</p>
   */
  @Override
  public void readCompleted() {
    playerConnection.flush();
    packetsFlushed = 0;
  }

  /**
   * Called when an exception occurs in the backend connection.
   *
   * <p>Passes the exception to the player and initiates error handling logic.</p>
   *
   * @param throwable the exception
   */
  @Override
  public void exception(final Throwable throwable) {
    exceptionTriggered = true;
    boolean safe = !(throwable instanceof ReadTimeoutException)
        || server.getConfiguration().isFailoverOnUnexpectedServerDisconnect();
    serverConn.getPlayer().handleConnectionException(serverConn.getServer(), throwable, safe);
  }

  /**
   * Gets the {@link VelocityServer} associated with this handler.
   *
   * @return the Velocity server
   */
  public VelocityServer getServer() {
    return server;
  }

  /**
   * Called when the backend connection is closed.
   *
   * <p>If the disconnect was unexpected, the player is either kicked or fallback logic is triggered,
   * depending on the proxy configuration.</p>
   */
  @Override
  public void disconnected() {
    serverConn.getServer().removePlayer(serverConn.getPlayer());
    if (!serverConn.isGracefulDisconnect() && !exceptionTriggered) {
      if (server.getConfiguration().isFailoverOnUnexpectedServerDisconnect()) {
        serverConn.getPlayer().handleConnectionException(serverConn.getServer(),
            DisconnectPacket.create(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR,
                serverConn.getPlayer().getProtocolVersion(),
                    serverConn.getPlayer().getConnection().getState()), true);
      } else {
        serverConn.getPlayer().disconnect(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
      }
    }
  }

  /**
   * Called when the backend connection's writability changes due to backpressure.
   *
   * <p>Pauses or resumes auto-reading from the player connection accordingly.</p>
   */
  @Override
  public void writabilityChanged() {
    Channel serverChan = serverConn.ensureConnected().getChannel();
    boolean writable = serverChan.isWritable();

    if (BACKPRESSURE_LOG) {
      if (writable) {
        logger.info("{} is not writable, not auto-reading player connection data", this.serverConn);
      } else {
        logger.info("{} is writable, will auto-read player connection data", this.serverConn);
      }
    }

    playerConnection.setAutoReading(writable);
  }
}
