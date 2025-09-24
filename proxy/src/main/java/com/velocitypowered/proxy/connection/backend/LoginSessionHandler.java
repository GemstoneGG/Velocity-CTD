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

import com.velocitypowered.api.event.player.CookieRequestEvent;
import com.velocitypowered.api.event.player.ServerLoginPluginMessageEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnteredConfigurationEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.PlayerInfoForwarding;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.PlayerDataForwarding;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.ClientboundCookieRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundStoreCookiePacket;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.EncryptionRequestPacket;
import com.velocitypowered.proxy.protocol.packet.LoginAcknowledgedPacket;
import com.velocitypowered.proxy.protocol.packet.LoginPluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponsePacket;
import com.velocitypowered.proxy.protocol.packet.ServerLoginSuccessPacket;
import com.velocitypowered.proxy.protocol.packet.SetCompressionPacket;
import com.velocitypowered.proxy.util.except.QuietRuntimeException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;

/**
 * Handles a player trying to log into the proxy.
 */
public class LoginSessionHandler implements MinecraftSessionHandler {

  static {
    LogManager.getLogger(LoginSessionHandler.class);
  }

  /**
   * The message displayed to a player when modern forwarding fails due to missing response.
   */
  private static final Component MODERN_IP_FORWARDING_FAILURE = Component.translatable("velocity.error.modern-forwarding-failed");

  /**
   * The Velocity server instance.
   */
  private final VelocityServer server;

  /**
   * The server connection associated with this login session.
   */
  private final VelocityServerConnection serverConn;

  /**
   * The future that completes with the connection result or failure.
   */
  private final CompletableFuture<Impl> resultFuture;

  /**
   * Whether forwarding data has already been sent to the backend server.
   */
  private boolean informationForwarded;

  LoginSessionHandler(final VelocityServer server, final VelocityServerConnection serverConn,
                      final CompletableFuture<Impl> resultFuture) {
    this.server = server;
    this.serverConn = serverConn;
    this.resultFuture = resultFuture;
  }

  /**
   * Handles an unexpected {@link EncryptionRequestPacket} from the backend server.
   *
   * <p>Velocity only supports offline-mode backend servers. If a backend sends this packet,
   * it indicates the server is in online mode, which is not allowed. An exception is thrown.</p>
   *
   * @param packet the encryption request packet
   * @return never returns normally; always throws an exception
   * @throws IllegalStateException if this packet is received
   */
  @Override
  public boolean handle(final EncryptionRequestPacket packet) {
    throw new IllegalStateException("Backend server is online-mode!");
  }

  /**
   * Handles a {@link LoginPluginMessagePacket} sent by the backend server.
   *
   * <p>If the plugin message is the {@code velocity:player_info} channel used for
   * modern IP forwarding, the proxy constructs and sends a {@link LoginPluginResponsePacket}
   * containing the signed forwarding data. If the message is unknown and the event is
   * subscribed, a {@link ServerLoginPluginMessageEvent} is fired asynchronously.</p>
   *
   * @param packet the login plugin message packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final LoginPluginMessagePacket packet) {
    MinecraftConnection mc = serverConn.ensureConnected();
    VelocityConfiguration configuration = server.getConfiguration();

    PlayerInfoForwarding forwardingMode = serverConn.getServer().getConfiguredPlayerInfoForwarding();

    if (forwardingMode == PlayerInfoForwarding.MODERN
        && packet.getChannel().equals(PlayerDataForwarding.CHANNEL)) {

      int requestedForwardingVersion = PlayerDataForwarding.MODERN_DEFAULT;
      // Check the forwarding version
      if (packet.content().readableBytes() == 1) {
        requestedForwardingVersion = packet.content().readByte();
      }

      ConnectedPlayer player = serverConn.getPlayer();
      ByteBuf forwardingData = PlayerDataForwarding.createForwardingData(
          configuration.getForwardingSecret(),
          serverConn.getPlayerRemoteAddressAsString(),
          player.getProtocolVersion(),
          player.getGameProfile(),
          player.getIdentifiedKey(),
          requestedForwardingVersion);

      LoginPluginResponsePacket response = new LoginPluginResponsePacket(packet.getId(), true, forwardingData);
      mc.write(response);
      informationForwarded = true;
    } else {
      // Don't understand, fire event if we have subscribers
      if (!this.server.getEventManager().hasSubscribers(ServerLoginPluginMessageEvent.class)) {
        mc.write(new LoginPluginResponsePacket(packet.getId(), false, Unpooled.EMPTY_BUFFER));
        return true;
      }

      final byte[] contents = ByteBufUtil.getBytes(packet.content());
      final MinecraftChannelIdentifier identifier = MinecraftChannelIdentifier.from(packet.getChannel());
      this.server.getEventManager().fire(new ServerLoginPluginMessageEvent(serverConn, identifier,
              contents, packet.getId()))
          .thenAcceptAsync(event -> {
            if (event.getResult().isAllowed()) {
              mc.write(new LoginPluginResponsePacket(packet.getId(), true, Unpooled
                  .wrappedBuffer(event.getResult().getResponse())));
            } else {
              mc.write(new LoginPluginResponsePacket(packet.getId(), false, Unpooled.EMPTY_BUFFER));
            }
          }, mc.eventLoop());
    }

    return true;
  }

  /**
   * Handles a {@link DisconnectPacket} received from the backend during login.
   *
   * <p>The disconnect reason is passed to the connection result future, and
   * the backend connection is closed.</p>
   *
   * @param packet the disconnect packet
   * @return {@code true} always
   */
  @Override
  public boolean handle(final DisconnectPacket packet) {
    resultFuture.complete(ConnectionRequestResults.forDisconnect(packet, serverConn.getServer()));
    serverConn.disconnect();
    return true;
  }

  /**
   * Handles a {@link SetCompressionPacket} during login.
   *
   * <p>This sets the compression threshold for the backend connection.</p>
   *
   * @param packet the set compression packet
   * @return {@code true} if the threshold was applied
   */
  @Override
  public boolean handle(final SetCompressionPacket packet) {
    serverConn.ensureConnected().setCompressionThreshold(packet.getThreshold());
    return true;
  }

  /**
   * Handles a {@link ServerLoginSuccessPacket} indicating successful login to the backend server.
   *
   * <p>If modern IP forwarding was required but never occurred, the connection is aborted.
   * Otherwise, the session transitions into PLAY or CONFIGURATION depending on the protocol version.
   * For initial joins, {@link PlayerEnteredConfigurationEvent} is fired. For backend switching,
   * configuration proceeds via {@link ConfigSessionHandler}.</p>
   *
   * @param packet the login success packet
   * @return {@code true} if login transition succeeded or was aborted correctly
   */
  @Override
  public boolean handle(final ServerLoginSuccessPacket packet) {
    PlayerInfoForwarding forwardingMode = serverConn.getServer().getConfiguredPlayerInfoForwarding();

    if (forwardingMode == PlayerInfoForwarding.MODERN && !informationForwarded) {
      resultFuture.complete(ConnectionRequestResults.forDisconnect(MODERN_IP_FORWARDING_FAILURE, serverConn.getServer()));
      serverConn.disconnect();
      return true;
    }

    // The player has been logged on to the backend server, but we're not done yet. There could be
    // other problems that could arise before we get a JoinGame packet from the server.

    // Move into the PLAY phase.
    MinecraftConnection smc = serverConn.ensureConnected();
    if (smc.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
      smc.setActiveSessionHandler(StateRegistry.PLAY, new TransitionSessionHandler(server, serverConn, resultFuture));
    } else {
      smc.write(new LoginAcknowledgedPacket());
      smc.setActiveSessionHandler(StateRegistry.CONFIG, new ConfigSessionHandler(server, serverConn, resultFuture));
      ConnectedPlayer player = serverConn.getPlayer();
      if (player.getClientSettingsPacket() != null) {
        smc.write(player.getClientSettingsPacket());
      }

      if (player.getConnection().getActiveSessionHandler() instanceof ClientPlaySessionHandler clientPlaySessionHandler) {
        smc.setAutoReading(false);
        clientPlaySessionHandler.doSwitch().thenAcceptAsync((unused) -> smc.setAutoReading(true), smc.eventLoop());
      } else {
        // Initial login - the player is already in configuration state.
        server.getEventManager().fireAndForget(new PlayerEnteredConfigurationEvent(player, serverConn));
      }
    }

    return true;
  }

  /**
   * Handles an unexpected {@link ClientboundStoreCookiePacket} during login.
   *
   * <p>Cookies can only be exchanged once the client has entered the CONFIGURATION or PLAY state.
   * Receiving this packet in the login phase indicates a backend protocol violation.</p>
   *
   * @param packet the cookie store packet
   * @throws IllegalStateException if called in the login phase
   */
  @Override
  public boolean handle(final ClientboundStoreCookiePacket packet) {
    throw new IllegalStateException("Can only store cookie in CONFIGURATION or PLAY protocol");
  }

  /**
   * Handles a {@link ClientboundCookieRequestPacket} sent by the backend during login.
   *
   * <p>This fires a {@link CookieRequestEvent} to determine whether to forward the cookie request
   * to the player client.</p>
   *
   * @param packet the cookie request packet
   * @return {@code true} if the event was fired and handled
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
   * Handles an exception thrown during login.
   *
   * <p>The exception is propagated to the login result future and will be processed upstream.</p>
   *
   * @param throwable the exception that occurred
   */
  @Override
  public void exception(final Throwable throwable) {
    resultFuture.completeExceptionally(throwable);
  }

  /**
   * Called when the backend connection is closed before login completes.
   *
   * <p>If legacy forwarding was configured, this throws a helpful error explaining that
   * BungeeCord forwarding must be enabled on the backend server. Otherwise, a generic
   * error is returned to the player.</p>
   */
  @Override
  public void disconnected() {
    PlayerInfoForwarding forwardingMode = serverConn.getServer().getConfiguredPlayerInfoForwarding();

    if (forwardingMode == PlayerInfoForwarding.LEGACY) {
      resultFuture.completeExceptionally(new QuietRuntimeException(
              """
              The connection to the remote server was unexpectedly closed.
              This is usually because the remote server does not have \
              BungeeCord IP forwarding correctly enabled.
              See https://docs.papermc.io/velocity/player-information-forwarding for instructions \
              on how to configure player info forwarding correctly."""));
    } else {
      resultFuture.completeExceptionally(
          new QuietRuntimeException("The connection to the remote server was unexpectedly closed.")
      );
    }
  }
}
