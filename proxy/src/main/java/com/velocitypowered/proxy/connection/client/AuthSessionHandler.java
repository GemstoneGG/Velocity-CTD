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

import static com.velocitypowered.api.network.ProtocolVersion.MINECRAFT_1_8;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.player.CookieReceiveEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.server.PlayerInfoForwarding;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.LoginAcknowledgedPacket;
import com.velocitypowered.proxy.protocol.packet.ServerLoginSuccessPacket;
import com.velocitypowered.proxy.protocol.packet.ServerboundCookieResponsePacket;
import com.velocitypowered.proxy.protocol.packet.SetCompressionPacket;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A session handler that is activated to complete the login phase.
 */
public class AuthSessionHandler implements MinecraftSessionHandler {

  /**
   * Logger for textual log messages.
   */
  private static final Logger LOGGER = LogManager.getLogger(AuthSessionHandler.class, new ParameterizedMessageFactory());

  /**
   * Logger for Adventure components.
   */
  private static final ComponentLogger COMPONENT_LOGGER = ComponentLogger.logger(AuthSessionHandler.class);

  /**
   * The proxy server instance.
   */
  private final VelocityServer server;

  /**
   * The Minecraft connection associated with this session.
   */
  private final MinecraftConnection mcConnection;

  /**
   * The inbound login connection.
   */
  private final LoginInboundConnection inbound;

  /**
   * The game profile of the connecting player.
   */
  private GameProfile profile;

  /**
   * The connected player, once authentication has completed.
   */
  private @MonotonicNonNull ConnectedPlayer connectedPlayer;

  /**
   * Whether the proxy is operating in online mode for this session.
   */
  private final boolean onlineMode;

  /**
   * The current login state of this connection.
   * Was implemented in Minecraft 1.20.2.
   */
  private State loginState = State.START;

  /**
   * The server ID hash sent to Mojang for authentication, or {@code null} if offline-mode.
   */
  private final String serverIdHash;

  /**
   * The minimum Minecraft version allowed to connect.
   */
  private final String minimumVersion;

  /**
   * The maximum Minecraft version allowed to connect.
   */
  private final String maximumVersion;

  AuthSessionHandler(final VelocityServer server, final LoginInboundConnection inbound,
                     final GameProfile profile, final boolean onlineMode, final String serverIdHash) {
    this.server = Preconditions.checkNotNull(server, "server");
    this.inbound = Preconditions.checkNotNull(inbound, "inbound");
    this.profile = Preconditions.checkNotNull(profile, "profile");
    this.onlineMode = onlineMode;
    this.mcConnection = inbound.delegatedConnection();
    this.serverIdHash = serverIdHash;
    this.minimumVersion = server.getConfiguration().getMinimumVersion();
    this.maximumVersion = server.getConfiguration().getMaximumVersion()
        .orElse(ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion());
  }

  /**
   * Called when this session handler is activated.
   *
   * <p>Performs version checks, fires a {@link GameProfileRequestEvent}, and initializes the {@link ConnectedPlayer}.
   * If allowed, transitions into permission setup and then continues with login protocol completion.
   * Players using older protocol versions or failing validation will be disconnected.</p>
   */
  @Override
  public void activated() {
    // Some connection types may need to alter the game profile.
    profile = mcConnection.getType().addGameProfileTokensIfRequired(profile, server.getConfiguration().getPlayerInfoForwardingMode());
    GameProfileRequestEvent profileRequestEvent = new GameProfileRequestEvent(inbound, profile, onlineMode);
    final GameProfile finalProfile = profile;

    // Make sure the player is on the minimum version set in configuration or higher
    if (!versionCheck(mcConnection)) {
      if (server.getConfiguration().isLogOfflineConnections() || (!server.getConfiguration().isLogMinimumVersion())) {
        return;
      }

      final String discMessage = String.format("[initial connection] %s (%s) has disconnected: ",
          finalProfile.getName(),
          mcConnection.getRemoteAddress().toString());

      COMPONENT_LOGGER.info(Component.text(discMessage).append(
          Component.translatable("velocity.error.modern-forwarding-needs-new-client", NamedTextColor.RED)
              .arguments(
                  Argument.string("min", minimumVersion),
                  Argument.string("max", maximumVersion))));
      return;
    }

    server.getEventManager().fire(profileRequestEvent).thenComposeAsync(profileEvent -> {
      if (mcConnection.isClosed()) {
        // The player disconnected after we authenticated them.
        return CompletableFuture.completedFuture(null);
      }

      // Initiate a regular connection and move over to it.
      ConnectedPlayer player = new ConnectedPlayer(server, profileEvent.getGameProfile(),
          mcConnection, inbound.getVirtualHost().orElse(null), inbound.getRawVirtualHost().orElse(null), onlineMode,
          inbound.getHandshakeIntent(), inbound.getIdentifiedKey());
      this.connectedPlayer = player;
      if (!server.canRegisterConnection(player)) {
        player.disconnect0(
            Component.translatable("velocity.error.already-connected-proxy", NamedTextColor.RED),
            true);
        return CompletableFuture.completedFuture(null);
      }

      if (server.getConfiguration().isLogPlayerConnections()) {
        LOGGER.info("{} has connected", player);
      }

      return server.getEventManager()
          .fire(new PermissionsSetupEvent(player, ConnectedPlayer.DEFAULT_PERMISSIONS))
          .thenAcceptAsync(event -> {
            if (!mcConnection.isClosed()) {
              // wait for permissions to load, then set the players permission function
              final PermissionFunction function = event.createFunction(player);
              if (function == null) {
                LOGGER.error("A plugin permission provider {} provided an invalid permission "
                        + "function for player {}. This is a bug in the plugin, not in "
                        + "Velocity. Falling back to the default permission function.",
                    event.getProvider().getClass().getName(), player.getUsername());
              } else {
                player.setPermissionFunction(function);
              }
              startLoginCompletion(player);
            }
          }, mcConnection.eventLoop());
    }, mcConnection.eventLoop()).exceptionally((ex) -> {
      LOGGER.error("Exception during connection of {}", finalProfile, ex);
      return null;
    });
  }

  private boolean versionCheck(final MinecraftConnection connection) {
    final ProtocolVersion minimumProtocolVersion = ProtocolVersion.getVersionByName(minimumVersion);
    final ProtocolVersion maximumProtocolVersion = ProtocolVersion.getVersionByName(maximumVersion);
    final String clientProtocolVersion = connection.getProtocolVersion().getVersionIntroducedIn();

    // Compare the client's protocol version with the minimum and maximum required versions
    if (ProtocolVersion.getVersionByName(clientProtocolVersion).lessThan(minimumProtocolVersion)
        || ProtocolVersion.getVersionByName(clientProtocolVersion).greaterThan(maximumProtocolVersion)) {
      this.inbound.disconnect(Component.translatable("velocity.error.modern-forwarding-needs-new-client", NamedTextColor.RED)
          .arguments(
              Argument.string("min", minimumVersion),
              Argument.string("max", maximumVersion)));
      return false;
    }

    return true;
  }

  private void startLoginCompletion(final ConnectedPlayer player) {
    int threshold = server.getConfiguration().getCompressionThreshold();
    if (threshold >= 0 && mcConnection.getProtocolVersion().noLessThan(MINECRAFT_1_8)) {
      mcConnection.write(new SetCompressionPacket(threshold));
      mcConnection.setCompressionThreshold(threshold);
    }

    VelocityConfiguration configuration = server.getConfiguration();
    UUID playerUniqueId = player.getUniqueId();
    if (configuration.getPlayerInfoForwardingMode() == PlayerInfoForwarding.NONE) {
      playerUniqueId = UuidUtils.generateOfflinePlayerUuid(player.getUsername());
    }

    if (player.getIdentifiedKey() != null) {
      final IdentifiedKey playerKey = player.getIdentifiedKey();
      if (playerKey.getSignatureHolder() == null) {
        if (playerKey instanceof IdentifiedKeyImpl unlinkedKey) {
          // Failsafe
          if (!unlinkedKey.internalAddHolder(player.getUniqueId())) {
            if (onlineMode) {
              inbound.disconnect(
                  Component.translatable("multiplayer.disconnect.invalid_public_key"));
              return;
            } else {
              LOGGER.warn("Key for player {} could not be verified!", player.getUsername());
            }
          }
        } else {
          LOGGER.warn("A custom key type has been set for player {}", player.getUsername());
        }
      } else {
        if (!Objects.equals(playerKey.getSignatureHolder(), playerUniqueId)) {
          LOGGER.warn("UUID for Player {} mismatches! "
              + "Chat/Commands signatures will not work correctly for this player!",
                  player.getUsername());
        }
      }
    }

    completeLoginProtocolPhaseAndInitialize(player);
  }

  /**
   * Handles a {@link LoginAcknowledgedPacket} from the client confirming receipt of the login success.
   *
   * <p>If the state is valid, switches the session to {@link ClientConfigSessionHandler} (1.20.2+) or proceeds
   * to post-login events and connects the player to their initial server (older versions).</p>
   *
   * @param packet the login acknowledgment packet
   * @return {@code true} if handled successfully
   */
  @Override
  public boolean handle(final LoginAcknowledgedPacket packet) {
    if (loginState != State.SUCCESS_SENT) {
      inbound.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_data"));
    } else {
      loginState = State.ACKNOWLEDGED;
      mcConnection.setActiveSessionHandler(StateRegistry.CONFIG, new ClientConfigSessionHandler(server, connectedPlayer));

      server.getEventManager().fire(new PostLoginEvent(connectedPlayer)).thenCompose(ignored -> connectToInitialServer(connectedPlayer))
          .exceptionally((ex) -> {
            LOGGER.error("Exception while connecting {} to initial server", connectedPlayer, ex);
            return null;
          });
    }

    return true;
  }

  /**
   * Handles a {@link ServerboundCookieResponsePacket} received from the client during login.
   *
   * <p>Proxy plugins are not allowed to process cookie responses in this phase,
   * so an exception is thrown if any plugin previously initiated a cookie request.</p>
   *
   * @param packet the cookie response packet
   * @return {@code true} to continue handling
   * @throws IllegalStateException if a response is received in the login phase
   */
  @Override
  public boolean handle(final ServerboundCookieResponsePacket packet) {
    server.getEventManager()
        .fire(new CookieReceiveEvent(connectedPlayer, packet.getKey(), packet.getPayload()))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            // The received cookie must have been requested by a proxy plugin in login phase,
            // because if a backend server requests a cookie in login phase, the client is already
            // in config phase.
            // Therefore, the only way we receive a CookieResponsePacket from a
            // client in login phase is when a proxy plugin requested a cookie in login phase.
            throw new IllegalStateException(
                "A cookie was requested by a proxy plugin in login phase but the response wasn't handled");
          }
        }, mcConnection.eventLoop());

    return true;
  }

  private void completeLoginProtocolPhaseAndInitialize(final ConnectedPlayer player) {
    mcConnection.setAssociation(player);

    server.getEventManager().fire(new LoginEvent(player, serverIdHash)).thenAcceptAsync(event -> {
      if (mcConnection.isClosed()) {
        // The player was disconnected
        server.getEventManager().fireAndForget(new DisconnectEvent(player,
            DisconnectEvent.LoginStatus.CANCELLED_BY_USER_BEFORE_COMPLETE));
        return;
      }

      Optional<Component> reason = event.getResult().getReasonComponent();
      if (reason.isPresent()) {
        player.disconnect0(reason.get(), true);
      } else {
        if (!server.registerConnection(player)) {
          player.disconnect0(Component.translatable("velocity.error.already-connected-proxy"), true);
          return;
        }

        if (!this.server.getClusterPlayerService().onPlayerConnect(player)) {
          return;
        }

        player.fullyConnected();

        ServerLoginSuccessPacket success = new ServerLoginSuccessPacket();
        success.setUsername(player.getUsername());
        success.setProperties(player.getGameProfileProperties());
        success.setUuid(player.getUniqueId());
        mcConnection.write(success);

        loginState = State.SUCCESS_SENT;
        if (inbound.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
          loginState = State.ACKNOWLEDGED;
          mcConnection.setActiveSessionHandler(StateRegistry.PLAY, new InitialConnectSessionHandler(player, server));
          server.getEventManager().fire(new PostLoginEvent(player)).thenCompose((ignored) ->
              connectToInitialServer(player)).exceptionally((ex) -> {
                LOGGER.error("Exception while connecting {} to initial server", player, ex);
                return null;
              });
        }
      }
    }, mcConnection.eventLoop()).exceptionally((ex) -> {
      LOGGER.error("Exception while completing login initialisation phase for {}", player, ex);
      return null;
    });
  }

  private CompletableFuture<Void> connectToInitialServer(final ConnectedPlayer player) {
    Optional<VelocityRegisteredServer> initialFromConfig = player.currentServerRetrySession().getNextServerToTry();
    PlayerChooseInitialServerEvent event =
        new PlayerChooseInitialServerEvent(player, initialFromConfig.orElse(null));

    return server.getEventManager().fire(event).thenRunAsync(() -> {
      // cast required (api event class)
      VelocityRegisteredServer toTry = (VelocityRegisteredServer) event.getInitialServer().orElse(null);
      if (toTry == null) {
        if (event.getReason().isPresent()) {
          player.disconnect0(event.getReason().get(), true);
        } else {
          player.disconnect0(
              Component.translatable("velocity.error.no-available-servers", NamedTextColor.RED), true);
        }

        return;
      }
      player.createConnectionRequest(toTry).fireAndForget();
    }, mcConnection.eventLoop());
  }

  /**
   * Handles an unknown or unexpected packet during the login phase.
   *
   * <p>The connection is immediately closed as unexpected input indicates a protocol violation.</p>
   *
   * @param buf the raw packet data
   */
  @Override
  public void handleUnknown(final ByteBuf buf) {
    mcConnection.close(true);
  }

  /**
   * Called when the client disconnects during the login process.
   *
   * <p>Cleans up the {@link ConnectedPlayer} if present and also invokes login-level cleanup
   * routines on the inbound connection.</p>
   */
  @Override
  public void disconnected() {
    if (connectedPlayer != null) {
      connectedPlayer.teardown();
    }

    this.inbound.cleanup();
  }

  enum State {

    /**
     * The initial state before a successful login has been sent.
     */
    START,

    /**
     * The server has sent the {@link ServerLoginSuccessPacket}, but the client has not acknowledged it yet.
     */
    SUCCESS_SENT,

    /**
     * The client has acknowledged the login, and the session is transitioning to the play or config state.
     */
    ACKNOWLEDGED
  }
}
