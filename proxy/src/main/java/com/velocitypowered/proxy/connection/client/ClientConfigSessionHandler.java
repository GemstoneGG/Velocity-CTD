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

package com.velocitypowered.proxy.connection.client;

import com.velocityctd.api.event.player.configuration.PlayerConfigurationResourcePackEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.CookieReceiveEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.event.player.configuration.PlayerFinishConfigurationEvent;
import com.velocitypowered.api.event.player.configuration.PlayerFinishedConfigurationEvent;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.backend.BungeeCordMessageResponder;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.player.resourcepack.ResourcePackResponseBundle;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.PingIdentifyPacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.ResourcePackResponsePacket;
import com.velocitypowered.proxy.protocol.packet.ServerboundCookieResponsePacket;
import com.velocitypowered.proxy.protocol.packet.ServerboundCustomClickActionPacket;
import com.velocitypowered.proxy.protocol.packet.config.CodeOfConductAcceptPacket;
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket;
import com.velocitypowered.proxy.protocol.packet.config.KnownPacksPacket;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Handles the client config stage.
 */
public class ClientConfigSessionHandler implements MinecraftSessionHandler {
  private static final boolean BACKPRESSURE_LOG =
      Boolean.getBoolean("velocity.log-server-backpressure");

  private static final Logger LOGGER = LogManager.getLogger(ClientConfigSessionHandler.class);

  // Backends don't send keepalives during configuration, so the proxy pings the client itself
  // while holding it to apply a pack.
  private static final long RESOURCE_PACK_KEEP_ALIVE_INTERVAL_SECONDS = 1L;

  // Disconnect an unresponsive client after this long, so it can't hold the decoupled backend (and
  // its growing packet buffer) open forever. A flat deadline, not reset by progress, so keep it
  // generous for slow large-pack downloads.
  private static final long RESOURCE_PACK_HOLD_CAP_SECONDS =
      Long.getLong("velocity-ctd.resource-pack-hold-cap-seconds", 60L);

  private final VelocityServer server;

  private final ConnectedPlayer player;

  private String brandChannel = null;

  private CompletableFuture<?> configurationFuture;

  private CompletableFuture<Void> configSwitchFuture;

  private boolean configuredOnce;

  // Active resource pack hold and its keepalive task, or null when no hold is in progress.
  private volatile @Nullable CompletableFuture<Void> resourcePackHold;
  private @Nullable ScheduledFuture<?> resourcePackKeepAlive;

  /**
   * Constructs a client config session handler.
   *
   * @param server the Velocity server instance
   * @param player the player
   */
  public ClientConfigSessionHandler(VelocityServer server, ConnectedPlayer player) {
    this.server = server;
    this.player = player;
  }

  @Override
  public void activated() {
    configSwitchFuture = new CompletableFuture<>();
  }

  @Override
  public void deactivated() {
    configurationFuture = null;
  }

  @Override
  public boolean handle(KeepAlivePacket packet) {
    player.forwardKeepAlive(packet);
    return true;
  }

  @Override
  public boolean handle(ClientSettingsPacket packet) {
    player.setClientSettings(packet);
    return true;
  }

  @Override
  public boolean handle(ResourcePackResponsePacket packet) {
    return player.resourcePackHandler().onResourcePackResponse(
        new ResourcePackResponseBundle(packet.getId(),
            packet.getHash(),
            packet.getStatus())
    );
  }

  @Override
  public boolean handle(FinishedUpdatePacket packet) {
    player.getConnection().setActiveSessionHandler(StateRegistry.PLAY, new ClientPlaySessionHandler(server, player));

    configSwitchFuture.complete(null);
    return true;
  }

  @Override
  public boolean handle(PluginMessagePacket packet) {
    VelocityServerConnection serverConn = player.getConnectionInFlight();
    if (PluginMessageUtil.isMcBrand(packet)) {
      String brand = PluginMessageUtil.readBrandMessage(packet.content());
      server.getEventManager().fireAndForget(new PlayerClientBrandEvent(player, brand));
      player.setClientBrand(brand);
      brandChannel = packet.getChannel();
      // Client sends `minecraft:brand` packet immediately after Login,
      // but at this time the backend server may not be ready
    } else if (BungeeCordMessageResponder.isBungeeCordMessage(packet)) {
      return true;
    } else if (serverConn != null) {
      byte[] bytes = ByteBufUtil.getBytes(packet.content());
      ChannelIdentifier id = this.server.getChannelRegistrar().getFromId(packet.getChannel());

      if (id == null) {
        serverConn.ensureConnected().write(packet.retain());
        return true;
      }

      // Handling this stuff async means that we should probably pause
      // the connection while we toss this off into another pool
      serverConn.getPlayer().getConnection().setAutoReading(false);
      this.server.getEventManager()
          .fire(new PluginMessageEvent(serverConn.getPlayer(), serverConn, id, bytes))
          .thenAcceptAsync(pme -> {
            if (pme.getResult().isAllowed() && serverConn.getConnection() != null) {
              serverConn.ensureConnected().write(new PluginMessagePacket(
                  pme.getIdentifier().getId(), Unpooled.wrappedBuffer(bytes)));
            }

            serverConn.getPlayer().getConnection().setAutoReading(true);
          }, player.getConnection().eventLoop()).exceptionally((ex) -> {
            LOGGER.error("Exception while handling plugin message packet for {}", player, ex);
            return null;
          });
    }

    return true;
  }

  @Override
  public boolean handle(PingIdentifyPacket packet) {
    if (player.getConnectionInFlight() != null) {
      player.getConnectionInFlight().ensureConnected().write(packet);
      return true;
    }

    return false;
  }

  @Override
  public boolean handle(KnownPacksPacket packet) {
    callConfigurationEvent().thenRun(() -> {
      VelocityServerConnection targetServer = player.getConnectionInFlightOrConnectedServer();
      if (targetServer != null) {
        targetServer.ensureConnected().write(packet);
      }
    }).exceptionally(ex -> {
      LOGGER.error("Error forwarding known packs response to backend:", ex);
      return null;
    });

    return true;
  }

  @Override
  public boolean handle(ServerboundCookieResponsePacket packet) {
    server.getEventManager()
        .fire(new CookieReceiveEvent(player, packet.getKey(), packet.getPayload()))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            VelocityServerConnection serverConnection = player.getConnectionInFlight();
            if (serverConnection != null) {
              Key resultedKey = event.getResult().getKey() == null
                  ? event.getOriginalKey() : event.getResult().getKey();
              byte[] resultedData = event.getResult().getData() == null
                  ? event.getOriginalData() : event.getResult().getData();

              serverConnection.ensureConnected().write(new ServerboundCookieResponsePacket(resultedKey, resultedData));
            }
          }
        }, player.getConnection().eventLoop());

    return true;
  }

  @Override
  public boolean handle(ServerboundCustomClickActionPacket packet) {
    if (player.getConnectionInFlight() != null) {
      player.getConnectionInFlight().ensureConnected().write(packet.retain());
      return true;
    }

    return false;
  }

  @Override
  public boolean handle(CodeOfConductAcceptPacket packet) {
    if (this.player.getConnectionInFlight() != null) {
      this.player.getConnectionInFlight().ensureConnected().write(packet);
      return true;
    }

    return false;
  }

  @Override
  public void handleGeneric(MinecraftPacket packet) {
    VelocityServerConnection serverConnection = player.getConnectedServer();
    if (serverConnection == null) {
      // No server connection yet, probably transitioning.
      return;
    }

    MinecraftConnection smc = serverConnection.getConnection();
    if (smc != null && serverConnection.getPhase().consideredComplete()) {
      if (packet instanceof PluginMessagePacket) {
        ((PluginMessagePacket) packet).retain();
      }

      smc.write(packet);
    }
  }

  @Override
  public void handleUnknown(ByteBuf buf) {
    VelocityServerConnection serverConnection = player.getConnectedServer();
    if (serverConnection == null) {
      // No server connection yet, probably transitioning.
      return;
    }

    MinecraftConnection smc = serverConnection.getConnection();
    if (smc != null && !smc.isClosed() && serverConnection.getPhase().consideredComplete()) {
      smc.write(buf.retain());
    }
  }

  @Override
  public void disconnected() {
    stopResourcePackKeepAlive();
    CompletableFuture<Void> hold = this.resourcePackHold;
    if (hold != null) {
      hold.complete(null);
    }

    player.teardown();
  }

  @Override
  public void exception(Throwable throwable) {
    player.disconnect(Component.translatable("velocity.error.player-connection-error", NamedTextColor.RED));
    if (MinecraftDecoder.DEBUG) {
      LOGGER.info("Exception while handling packet for {}", player, throwable);
    }
  }

  @Override
  public void writabilityChanged() {
    boolean writable = player.getConnection().getChannel().isWritable();

    if (BACKPRESSURE_LOG) {
      if (writable) {
        LOGGER.info("{} is writable, will auto-read backend connection data", player);
      } else {
        LOGGER.info("{} is not writable, not auto-reading backend connection data", player);
      }
    }

    if (!writable) {
      // Flush pending packets to free up memory. Schedule on a future event loop invocation
      // to avoid disabling auto-read while the flush resolves backpressure.
      player.getConnection().eventLoop().execute(() -> player.getConnection().flush());
    }

    VelocityServerConnection serverConn = player.getConnectionInFlightOrConnectedServer();
    if (serverConn != null) {
      MinecraftConnection smc = serverConn.getConnection();
      if (smc != null) {
        smc.setAutoReading(writable);
      }
    }
  }

  /**
   * Calls the {@link PlayerConfigurationEvent}.
   * For 1.20.5+ backends, this is done when the client responds to
   * the known packs request. The response is delayed until the event
   * has been called.
   * For 1.20.2-1.20.4 servers, this is done when the client acknowledges
   * the end of the configuration.
   * This is handled differently because for 1.20.5+ servers can't keep
   * their connection alive between states and older servers don't have
   * the known packs transaction.
   *
   * @return a {@link CompletableFuture} that completes when the configuration event is fired
   */
  private CompletableFuture<?> callConfigurationEvent() {
    if (configurationFuture != null) {
      return configurationFuture;
    }

    CompletableFuture<?> future = server.getEventManager().fire(new PlayerConfigurationEvent(player,
            player.getConnectionInFlightOrConnectedServer()));
    configurationFuture = future;
    return future;
  }

  /**
   * Handles the backend finishing the config stage.
   *
   * @param serverConn the server connection
   * @return a future that completes when the config stage is finished
   */
  public CompletableFuture<Void> handleBackendFinishUpdate(VelocityServerConnection serverConn) {
    MinecraftConnection smc = serverConn.ensureConnected();

    String brand = serverConn.getPlayer().getClientBrand();
    if (brand != null && brandChannel != null) {
      ByteBuf buf = Unpooled.buffer();
      ProtocolUtils.writeString(buf, brand);
      PluginMessagePacket brandPacket = new PluginMessagePacket(brandChannel, buf);
      smc.write(brandPacket);
    }

    boolean firstJoin = !configuredOnce;
    configuredOnce = true;

    callConfigurationEvent()
        .thenCompose(v -> applyConfigurationResourcePack(serverConn, firstJoin))
        .thenCompose(v -> server.getEventManager().fire(new PlayerFinishConfigurationEvent(player, serverConn))
        .completeOnTimeout(null, 5, TimeUnit.SECONDS)).thenRunAsync(() -> {
          if (player.getConnection().isClosed()) {
            return;
          }

          player.getConnection().write(FinishedUpdatePacket.INSTANCE);
          player.getConnection().getChannel().pipeline().get(MinecraftEncoder.class).setState(StateRegistry.PLAY);
          server.getEventManager().fireAndForget(new PlayerFinishedConfigurationEvent(player, serverConn));
        }, player.getConnection().eventLoop()).exceptionally(ex -> {
          LOGGER.error("Error finishing configuration state:", ex);
          return null;
        });

    return configSwitchFuture;
  }

  /**
   * Fires the {@link PlayerConfigurationResourcePackEvent} and, if a pack was set, holds the player
   * in configuration until every pack settles, or disconnects after
   * {@link #RESOURCE_PACK_HOLD_CAP_SECONDS}. The backend is advanced to PLAY separately (see
   * {@code ConfigSessionHandler}) so it doesn't time out during the hold.
   *
   * @param serverConn the server (re-)configuring the player
   * @param firstJoin  whether this is the initial configuration following login
   * @return a future completing once the pack(s) settle, or immediately if none were set
   */
  private CompletableFuture<Void> applyConfigurationResourcePack(VelocityServerConnection serverConn,
                                                                 boolean firstJoin) {
    PlayerConfigurationResourcePackEvent event =
        new PlayerConfigurationResourcePackEvent(player, serverConn, firstJoin);
    return server.getEventManager().fire(event).thenComposeAsync(result -> {
      ResourcePackRequest request = result.getResourcePack();
      if (request == null || player.getConnection().isClosed()) {
        return CompletableFuture.completedFuture(null);
      }

      CompletableFuture<Void> hold = player.resourcePackHandler().queueResourcePackAndAwait(request);
      this.resourcePackHold = hold;
      startResourcePackKeepAlive();

      // Disconnect a player who never responds within the cap.
      ScheduledFuture<?> holdCap = player.getConnection().eventLoop().schedule(() -> {
        if (!hold.isDone()) {
          player.disconnect(Component.translatable(
              "velocity.error.resource-pack-configuration-timeout", NamedTextColor.RED));
        }
      }, RESOURCE_PACK_HOLD_CAP_SECONDS, TimeUnit.SECONDS);

      return hold.whenCompleteAsync((v, t) -> {
        holdCap.cancel(false);
        stopResourcePackKeepAlive();
        this.resourcePackHold = null;
      }, player.getConnection().eventLoop()).exceptionally(t -> {
        LOGGER.error("Couldn't apply configuration resource pack for {}", player, t);
        return null;
      });
    }, player.getConnection().eventLoop());
  }

  private void startResourcePackKeepAlive() {
    if (resourcePackKeepAlive == null) {
      resourcePackKeepAlive = player.getConnection().eventLoop().scheduleAtFixedRate(
          player::sendKeepAlive,
          RESOURCE_PACK_KEEP_ALIVE_INTERVAL_SECONDS,
          RESOURCE_PACK_KEEP_ALIVE_INTERVAL_SECONDS,
          TimeUnit.SECONDS);
    }
  }

  private void stopResourcePackKeepAlive() {
    if (resourcePackKeepAlive != null) {
      resourcePackKeepAlive.cancel(false);
      resourcePackKeepAlive = null;
    }
  }
}
