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

import static com.velocitypowered.proxy.connection.forge.legacy.LegacyForgeConstants.HANDSHAKE_HOSTNAME_TOKEN;
import static com.velocitypowered.proxy.network.Connections.HANDLER;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.PluginMessageEncoder;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.PlayerInfoForwarding;
import com.velocitypowered.proxy.connection.ConnectionTypes;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftConnectionAssociation;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.PlayerDataForwarding;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.forge.modern.ModernForgeConnectionType;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.HandshakePacket;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket;
import com.velocitypowered.proxy.protocol.util.ByteBufDataOutput;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Handles a connection from the proxy to some backend server.
 */
public class VelocityServerConnection implements MinecraftConnectionAssociation, ServerConnection {

  /**
   * The server this connection is targeting.
   */
  private final VelocityRegisteredServer registeredServer;

  /**
   * The server the player was previously connected to, if any.
   */
  private final @Nullable VelocityRegisteredServer previousServer;

  /**
   * The player using this server connection.
   */
  private final ConnectedPlayer proxyPlayer;

  /**
   * The main Velocity server instance.
   */
  private final VelocityServer server;

  /**
   * The underlying Minecraft connection to the backend server.
   */
  private @Nullable MinecraftConnection connection;

  /**
   * Whether the server connection has fully completed the JoinGame phase.
   */
  private boolean hasCompletedJoin = false;

  /**
   * Whether the connection was disconnected gracefully (as opposed to a crash or forced close).
   */
  private boolean gracefulDisconnect = false;

  /**
   * The current backend connection phase for this server connection.
   */
  private BackendConnectionPhase connectionPhase = BackendConnectionPhases.UNKNOWN;

  /**
   * Pending ping IDs and the time they were sent (used for latency measurement).
   */
  private final Map<Long, Long> pendingPings = new HashMap<>();

  /**
   * The entity ID assigned to the player by the backend server.
   *
   * <p>Monotonically non-null: unset until known (typically after {@link JoinGamePacket}),
   * then set once and not reverted to {@code null}.</p>
   */
  private @MonotonicNonNull Integer entityId;

  /**
   * Initializes a new server connection.
   *
   * @param registeredServer the server to connect to
   * @param previousServer   the server the player is coming from
   * @param proxyPlayer      the player connecting to the server
   * @param server           the Velocity proxy instance
   */
  public VelocityServerConnection(final VelocityRegisteredServer registeredServer,
                                  @Nullable final VelocityRegisteredServer previousServer,
                                  final ConnectedPlayer proxyPlayer, final VelocityServer server) {
    this.registeredServer = registeredServer;
    this.previousServer = previousServer;
    this.proxyPlayer = proxyPlayer;
    this.server = server;
  }

  /**
   * Connects to the server.
   *
   * @return a {@link com.velocitypowered.api.proxy.ConnectionRequestBuilder.Result}
   *     representing whether the connection succeeded
   */
  public CompletableFuture<Impl> connect() {
    CompletableFuture<Impl> result = new CompletableFuture<>();
    // Note: we use the event loop for the connection the player is on. This reduces context
    // switches.
    server.createBootstrap(proxyPlayer.getConnection().eventLoop())
        .handler(server.getBackendChannelInitializer())
        .connect(registeredServer.getServerInfo().getAddress())
        .addListener((ChannelFutureListener) future -> {
          if (future.isSuccess()) {
            connection = new MinecraftConnection(future.channel(), server);
            connection.setAssociation(VelocityServerConnection.this);
            future.channel().pipeline().addLast(HANDLER, connection);

            // Kick off the connection process
            if (!connection.setActiveSessionHandler(StateRegistry.HANDSHAKE)) {
              MinecraftSessionHandler handler =
                  new LoginSessionHandler(server, VelocityServerConnection.this, result);
              connection.setActiveSessionHandler(StateRegistry.HANDSHAKE, handler);
              connection.addSessionHandler(StateRegistry.LOGIN, handler);
            }

            // Set the connection phase, which may, for future forge (or whatever), be
            // determined
            // at this point already
            connectionPhase = connection.getType().getInitialBackendPhase();
            startHandshake();
          } else {
            // Complete the result immediately. ConnectedPlayer will reset the in-flight
            // connection.
            result.completeExceptionally(future.cause());
          }
        });

    return result;
  }

  /**
   * Gets the remote IP address of the player in string format, stripping any IPv6 scope suffix.
   *
   * @return the player's IP address as a string
   */
  String getPlayerRemoteAddressAsString() {
    final String addr = proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
    int ipv6ScopeIdx = addr.indexOf('%');
    if (ipv6ScopeIdx == -1) {
      return addr;
    } else {
      return addr.substring(0, ipv6ScopeIdx);
    }
  }

  private String createLegacyForwardingAddress() {
    return PlayerDataForwarding.createLegacyForwardingAddress(
      proxyPlayer.getVirtualHost().orElseGet(() ->
        registeredServer.getServerInfo().getAddress()).getHostString(),
      getPlayerRemoteAddressAsString(),
      proxyPlayer.getGameProfile()
    );
  }

  private String createBungeeGuardForwardingAddress(final byte[] forwardingSecret) {
    return PlayerDataForwarding.createBungeeGuardForwardingAddress(
      proxyPlayer.getVirtualHost().orElseGet(() ->
        registeredServer.getServerInfo().getAddress()).getHostString(),
      getPlayerRemoteAddressAsString(),
      proxyPlayer.getGameProfile(),
      forwardingSecret
    );
  }

  private void startHandshake() {
    final MinecraftConnection mc = ensureConnected();

    PlayerInfoForwarding forwardingMode = registeredServer.getConfiguredPlayerInfoForwarding();

    // Initiate the handshake.
    ProtocolVersion protocolVersion = proxyPlayer.getConnection().getProtocolVersion();
    String playerVhost = proxyPlayer.getVirtualHost()
        .orElseGet(() -> registeredServer.getServerInfo().getAddress())
        .getHostString();

    HandshakePacket handshake = new HandshakePacket();
    handshake.setIntent(HandshakeIntent.LOGIN);
    handshake.setProtocolVersion(protocolVersion);
    if (forwardingMode == PlayerInfoForwarding.LEGACY) {
      handshake.setServerAddress(createLegacyForwardingAddress());
    } else if (forwardingMode == PlayerInfoForwarding.BUNGEEGUARD) {
      byte[] secret = server.getConfiguration().getForwardingSecret();
      handshake.setServerAddress(createBungeeGuardForwardingAddress(secret));
    } else if (proxyPlayer.getConnection().getType() == ConnectionTypes.LEGACY_FORGE) {
      handshake.setServerAddress(playerVhost + HANDSHAKE_HOSTNAME_TOKEN);
    } else if (proxyPlayer.getConnection().getType() instanceof ModernForgeConnectionType forgeConnection) {
      handshake.setServerAddress(playerVhost + forgeConnection.getModernToken());
    } else {
      handshake.setServerAddress(playerVhost);
    }

    handshake.setPort(proxyPlayer.getVirtualHost()
        .orElseGet(() -> registeredServer.getServerInfo().getAddress())
        .getPort());
    mc.delayedWrite(handshake);

    mc.setProtocolVersion(protocolVersion);
    mc.setActiveSessionHandler(StateRegistry.LOGIN);
    if (proxyPlayer.getIdentifiedKey() == null
        && proxyPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
      mc.delayedWrite(new ServerLoginPacket(proxyPlayer.getUsername(), proxyPlayer.getUniqueId()));
    } else {
      mc.delayedWrite(new ServerLoginPacket(proxyPlayer.getUsername(), proxyPlayer.getIdentifiedKey()));
    }
    mc.flush();
  }

  /**
   * Gets the current Minecraft connection to the backend server.
   *
   * @return the Minecraft connection, or {@code null} if not yet connected
   */
  public @Nullable MinecraftConnection getConnection() {
    return connection;
  }

  /**
   * Ensures the connection is still active and throws an exception if it is not.
   *
   * @return the active connection
   * @throws IllegalStateException if the connection is inactive
   */
  public MinecraftConnection ensureConnected() {
    if (connection == null) {
      throw new IllegalStateException("Not connected to server!");
    }

    return connection;
  }

  /**
   * Gets the {@link RegisteredServer} this connection is targeting.
   *
   * @return the registered server this connection points to
   */
  @Override
  public VelocityRegisteredServer getServer() {
    return registeredServer;
  }

  /**
   * Gets the previously connected server for the player, if any.
   *
   * @return an {@link Optional} containing the previous server or empty if not applicable
   */
  @Override
  public Optional<RegisteredServer> getPreviousServer() {
    return Optional.ofNullable(this.previousServer);
  }

  /**
   * Gets the {@link ServerInfo} associated with the target server.
   *
   * @return the {@link ServerInfo} of the destination server
   */
  @Override
  public ServerInfo getServerInfo() {
    return registeredServer.getServerInfo();
  }

  /**
   * Gets the {@link ConnectedPlayer} associated with this connection.
   *
   * @return the player associated with this server connection
   */
  @Override
  public ConnectedPlayer getPlayer() {
    return proxyPlayer;
  }

  /**
   * Disconnects from the server.
   */
  public void disconnect() {
    if (connection != null) {
      gracefulDisconnect = true;
      connection.close(false);
      connection = null;
    }
  }

  /**
   * Returns a debug-friendly string representation of this server connection.
   *
   * @return a string describing the connection (e.g. "[server connection] Player -> Server")
   */
  @Override
  public String toString() {
    return "[server connection] " + proxyPlayer.getGameProfile().getName() + " -> "
        + registeredServer.getServerInfo().getName();
  }

  /**
   * Sends a plugin message to the server using a raw byte array payload.
   *
   * @param identifier the plugin channel to send the message on
   * @param data       the raw message payload
   * @return {@code true} if the message was sent, {@code false} if the buffer was empty
   */
  @Override
  public boolean sendPluginMessage(final @NotNull ChannelIdentifier identifier, final byte @NotNull [] data) {
    return sendPluginMessage(identifier, Unpooled.wrappedBuffer(data));
  }

  /**
   * Sends a plugin message to the server using a {@link PluginMessageEncoder} to encode the payload.
   *
   * <p>If the resulting buffer is empty, the message will not be sent.</p>
   *
   * @param identifier   the plugin channel to send the message on
   * @param dataEncoder  the encoder used to write the message payload
   * @return {@code true} if the message was sent, {@code false} if the encoded payload was empty
   */
  @Override
  public boolean sendPluginMessage(final @NotNull ChannelIdentifier identifier, final @NotNull PluginMessageEncoder dataEncoder) {
    requireNonNull(identifier);
    requireNonNull(dataEncoder);
    final ByteBuf buf = Unpooled.buffer();
    final ByteBufDataOutput dataOutput = new ByteBufDataOutput(buf);
    dataEncoder.encode(dataOutput);
    if (buf.isReadable()) {
      return sendPluginMessage(identifier, buf);
    } else {
      buf.release();
      return false;
    }
  }

  /**
   * Sends a plugin message to the server through this connection.
   *
   * @param identifier the channel ID to use
   * @param data       the data
   * @return whether the message was sent
   */
  public boolean sendPluginMessage(final ChannelIdentifier identifier, final ByteBuf data) {
    Preconditions.checkNotNull(identifier, "identifier");
    Preconditions.checkNotNull(data, "data");

    final MinecraftConnection mc = ensureConnected();

    final PluginMessagePacket message = new PluginMessagePacket(identifier.getId(), data);
    mc.write(message);
    return true;
  }

  /**
   * Indicates that we have completed the plugin process.
   */
  public void completeJoin() {
    if (!hasCompletedJoin) {
      hasCompletedJoin = true;
      if (connectionPhase == BackendConnectionPhases.UNKNOWN) {
        // Now we know
        connectionPhase = BackendConnectionPhases.VANILLA;
        if (connection != null) {
          connection.setType(ConnectionTypes.VANILLA);
        }
      }
    }
  }

  /**
   * Returns whether the connection to the backend server was closed gracefully.
   *
   * @return {@code true} if the disconnect was cleanly handled, {@code false} otherwise
   */
  boolean isGracefulDisconnect() {
    return gracefulDisconnect;
  }

  /**
   * Gets the map of pending keep-alive pings sent to the backend server.
   * The map keys represent the ping ID and the values are the timestamps
   * (in nanoseconds) of when the ping was sent.
   *
   * @return the map of pending ping IDs and their send times
   */
  public Map<Long, Long> getPendingPings() {
    return pendingPings;
  }

  /**
   * Gets the entity ID assigned to the player by the backend server, if known.
   *
   * @return the entity ID, or {@code null} if not yet set
   */
  public Integer getEntityId() {
    return entityId;
  }

  /**
   * Sets the entity ID assigned to the player by the backend server.
   *
   * @param entityId the entity ID to set
   */
  public void setEntityId(final Integer entityId) {
    this.entityId = entityId;
  }

  /**
   * Ensures that this server connection remains "active": the connection is established and not
   * closed, the player is still connected to the server, and the player still remains online.
   *
   * @return whether the player is online
   */
  public boolean isActive() {
    return connection != null && !connection.isClosed() && !gracefulDisconnect && proxyPlayer.isActive();
  }

  /**
   * Gets the current "phase" of the connection, mostly used for tracking modded negotiation for
   * legacy forge servers and provides methods for performing phase-specific actions.
   *
   * @return The {@link BackendConnectionPhase}
   */
  public BackendConnectionPhase getPhase() {
    return connectionPhase;
  }

  /**
   * Sets the current "phase" of the connection. See {@link #getPhase()}
   *
   * @param connectionPhase The {@link BackendConnectionPhase}
   */
  public void setConnectionPhase(final BackendConnectionPhase connectionPhase) {
    this.connectionPhase = connectionPhase;
  }

  /**
   * Gets whether the {@link JoinGamePacket} packet has been
   * sent by this server.
   *
   * @return Whether the join has been completed.
   */
  public boolean hasCompletedJoin() {
    return hasCompletedJoin;
  }
}
