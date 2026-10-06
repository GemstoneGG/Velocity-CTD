/*
 * Copyright (C) 2018-2023 Velocity Contributors
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.velocityctd.api.event.connection.ConnectionEstablishEvent;
import com.velocitypowered.api.event.connection.ConnectionHandshakeEvent;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.ConnectionType;
import com.velocitypowered.proxy.connection.ConnectionTypes;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.forge.legacy.LegacyForgeConstants;
import com.velocitypowered.proxy.connection.forge.modern.ModernForgeConnectionType;
import com.velocitypowered.proxy.connection.forge.modern.ModernForgeConstants;
import com.velocitypowered.proxy.connection.util.VelocityInboundConnection;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.HandshakePacket;
import com.velocitypowered.proxy.protocol.packet.LegacyDisconnect;
import com.velocitypowered.proxy.protocol.packet.LegacyHandshakePacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPingPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * The initial handler used when a connection is established to the proxy. This will either
 * transition to {@link StatusSessionHandler} or {@link InitialLoginSessionHandler} as soon as the
 * handshake packet is received.
 */
public class HandshakeSessionHandler implements MinecraftSessionHandler {

  private static final Logger LOGGER = LogManager.getLogger(HandshakeSessionHandler.class);
  private static final int MAX_HELD_PACKETS = 16;
  private static final long LOGIN_TIMEOUT_MILLIS =
      Long.getLong("velocity.login-timeout", 30_000);

  private final MinecraftConnection connection;
  private final VelocityServer server;

  /**
   * The configured minimum version string used to validate connecting clients.
   */
  private final String minimumVersion;

  /**
   * The configured maximum version string used to validate connecting clients.
   */
  private final String maximumVersion;

  /**
   * Packets the client sent behind its handshake, held while a {@link ConnectionEstablishEvent}
   * listener decides on the connection; {@code null} when nothing is being decided.
   */
  private @Nullable Queue<Object> heldPackets;

  public HandshakeSessionHandler(MinecraftConnection connection, VelocityServer server) {
    this.connection = Preconditions.checkNotNull(connection, "connection");
    this.server = Preconditions.checkNotNull(server, "server");
    this.minimumVersion = server.getConfiguration().getMinimumVersion();
    this.maximumVersion = server.getConfiguration().getMaximumVersion()
        .orElse(ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion());
  }

  @Override
  public boolean handle(LegacyPingPacket packet) {
    connection.setProtocolVersion(ProtocolVersion.LEGACY);
    final StatusSessionHandler handler =
        new StatusSessionHandler(server, new LegacyInboundConnection(connection, packet));
    connection.setActiveSessionHandler(StateRegistry.STATUS, handler);
    handler.handle(packet);
    return true;
  }

  @Override
  public boolean handle(LegacyHandshakePacket packet) {
    connection.closeWith(LegacyDisconnect.from(Component.text(
        "Your client is extremely old. Please update to a newer version of Minecraft.",
        NamedTextColor.RED)
    ));
    return true;
  }

  @Override
  public boolean handle(final HandshakePacket handshake) {
    final StateRegistry nextState = getStateForProtocol(handshake.getNextStatus());
    if (nextState == null) {
      LOGGER.error("{} provided invalid protocol {}", this, handshake.getNextStatus());
      connection.close(true);
      return true;
    }

    InitialInboundConnection ic = new InitialInboundConnection(connection, cleanVhost(handshake.getServerAddress()), handshake);
    CompletableFuture<ConnectionEstablishEvent> establish = server.getEventManager()
        .fire(new ConnectionEstablishEvent(ic, handshake.getIntent()));
    if (establish.isDone() && !establish.isCompletedExceptionally()) {
      // Nobody listens off the event loop, so decide before the client's next packet is read.
      establish(handshake, nextState, ic, establish.getNow(null));
      return true;
    }

    // A listener decides off the event loop. The client sends its next packet right behind the
    // handshake, often in the same read, so decode what follows for the state the client moved to
    // and hold it until the listener has decided.
    connection.setProtocolVersion(handshake.getProtocolVersion());
    connection.setState(nextState);
    connection.setAutoReading(false);
    heldPackets = new ArrayDeque<>();
    establish.whenCompleteAsync(
        (result, throwable) -> establishHeld(handshake, nextState, ic, result, throwable),
        connection.eventLoop());
    return true;
  }

  private void establish(HandshakePacket handshake, StateRegistry nextState,
                         InitialInboundConnection ic, ConnectionEstablishEvent result) {
    if (!result.getResult().isAllowed()) {
      connection.close(true);
      return;
    }

    if (handshake.getIntent() == HandshakeIntent.TRANSFER && !server.getConfiguration().isAcceptTransfers()) {
      // Bump connection into correct protocol state so that we can send the disconnect packet.
      connection.setProtocolVersion(handshake.getProtocolVersion());
      connection.setState(StateRegistry.LOGIN);
      ic.disconnect(Component.translatable("multiplayer.disconnect.transfers_disabled"));
      return;
    }

    connection.setProtocolVersion(handshake.getProtocolVersion());
    connection.setAssociation(ic);

    switch (nextState) {
      case STATUS -> connection.setActiveSessionHandler(StateRegistry.STATUS, new StatusSessionHandler(server, ic));
      case LOGIN -> this.handleLogin(handshake, ic);
      default ->
      // If you get this, it's a bug in Velocity.
      throw new AssertionError("getStateForProtocol provided invalid state!");
    }
  }

  private void establishHeld(HandshakePacket handshake, StateRegistry nextState,
                             InitialInboundConnection ic, @Nullable ConnectionEstablishEvent result,
                             @Nullable Throwable throwable) {
    final Queue<Object> held = heldPackets;
    heldPackets = null;
    try {
      if (connection.isClosed() || held == null) {
        return;
      }
      if (throwable != null || result == null) {
        LOGGER.error("{}: exception while handling the connection establish event", this,
            throwable);
        connection.close(true);
        return;
      }

      establish(handshake, nextState, ic, result);
      if (connection.getActiveSessionHandler() == this || connection.isClosed()
          || connection.isKnownDisconnect()) {
        return;
      }

      // Reading resumes on a later pass of the event loop, so the held packets still go first, and
      // a pause one of them asks for is not undone.
      connection.setAutoReading(true);
      final ChannelHandlerContext ctx = connection.getChannel().pipeline().context(connection);
      Object packet;
      while (ctx != null && !connection.isClosed() && (packet = held.poll()) != null) {
        connection.channelRead(ctx, packet);
      }
    } catch (RuntimeException e) {
      LOGGER.error("{}: exception while establishing the connection", this, e);
      connection.close(true);
    } finally {
      releaseHeldPackets(held);
    }
  }

  /**
   * Holds a packet the client sent behind its handshake while a listener of the
   * {@link ConnectionEstablishEvent} decides on the connection. A client has no reason to send more
   * than a packet or two before it hears back, so a connection that sends more is closed.
   *
   * @param packet the packet to hold
   * @return whether the packet was held, or the connection closed for sending too many
   */
  private boolean hold(Object packet) {
    final Queue<Object> held = heldPackets;
    if (held == null) {
      return false;
    }

    if (held.size() >= MAX_HELD_PACKETS) {
      connection.close(true);
      return true;
    }
    held.add(ReferenceCountUtil.retain(packet));
    return true;
  }

  private static void releaseHeldPackets(@Nullable Queue<Object> held) {
    if (held == null) {
      return;
    }
    Object packet;
    while ((packet = held.poll()) != null) {
      ReferenceCountUtil.release(packet);
    }
  }

  private static @Nullable StateRegistry getStateForProtocol(int status) {
    return switch (status) {
      case StateRegistry.STATUS_ID -> StateRegistry.STATUS;
      case StateRegistry.LOGIN_ID, StateRegistry.TRANSFER_ID -> StateRegistry.LOGIN;
      default -> null;
    };
  }

  private void handleLogin(HandshakePacket handshake, InitialInboundConnection ic) {
    if (!handshake.getProtocolVersion().isSupported()) {
      // Bump connection into correct protocol state so that we can send the disconnect packet.
      connection.setState(StateRegistry.LOGIN);
      ic.disconnectQuietly(Component.translatable("velocity.error.modern-forwarding-needs-new-client")
          .arguments(
              Argument.string("min", minimumVersion),
              Argument.string("max", maximumVersion)));
      return;
    }

    final InetAddress address = ((InetSocketAddress) connection.getRemoteAddress()).getAddress();
    if (!server.getIpAttemptLimiter().attempt(address)) {
      // Bump connection into correct protocol state so that we can send the disconnect packet.
      connection.setState(StateRegistry.LOGIN);
      ic.disconnectQuietly(Component.translatable("velocity.error.logging-in-too-fast"));
      return;
    }

    connection.setType(this.getHandshakeConnectionType(handshake));

    // Note: We defer the modern forwarding version check until we actually know which server
    // the player is connecting to. This allows 1.7 clients to connect to servers using legacy forwarding
    // even when the global default is modern forwarding.

    final LoginInboundConnection lic = new LoginInboundConnection(ic);
    server.getEventManager().fireAndForget(
            new ConnectionHandshakeEvent(lic, handshake.getIntent()));
    connection.setActiveSessionHandler(StateRegistry.LOGIN,
        new InitialLoginSessionHandler(server, connection, lic));
    scheduleLoginTimeout(ic);
  }

  private void scheduleLoginTimeout(InitialInboundConnection ic) {
    if (LOGIN_TIMEOUT_MILLIS <= 0) {
      return;
    }
    final ScheduledFuture<?> timeout = connection.eventLoop().schedule(() -> {
      if (!connection.isClosed() && connection.getState() == StateRegistry.LOGIN) {
        ic.disconnectQuietly(Component.translatable("multiplayer.disconnect.slow_login"));
      }
    }, LOGIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    connection.getChannel().closeFuture().addListener(future -> timeout.cancel(false));
  }

  private ConnectionType getHandshakeConnectionType(HandshakePacket handshake) {

    if (server.getConfiguration().isDisableForge()) {
      return ConnectionTypes.VANILLA;
    }

    if (handshake.getServerAddress().contains(ModernForgeConstants.MODERN_FORGE_TOKEN)
            && handshake.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
      return new ModernForgeConnectionType(handshake.getServerAddress());
    }
    // Determine if we're using Forge (1.8 to 1.12, may not be the case in 1.13).
    if (handshake.getServerAddress().endsWith(LegacyForgeConstants.HANDSHAKE_HOSTNAME_TOKEN)
        && handshake.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_13)) {
      return ConnectionTypes.LEGACY_FORGE;
    } else if (handshake.getProtocolVersion().noGreaterThan(ProtocolVersion.MINECRAFT_1_7_6)) {
      // 1.7 Forge will not notify us during handshake. UNDETERMINED will listen for incoming
      // forge handshake attempts. Also sends a reset handshake packet on every transition.
      return ConnectionTypes.UNDETERMINED_17;
    } else {
      // Note for future implementation: Forge 1.13+ identifies itself using a slightly different
      // hostname token.
      return ConnectionTypes.VANILLA;
    }
  }

  /**
   * Cleans the specified virtual host hostname.
   *
   * @param hostname the host name to clean
   * @return the cleaned hostname
   */
  @VisibleForTesting
  static String cleanVhost(String hostname) {
    // Clean out any anything after any zero bytes (this includes BungeeCord forwarding and the
    // legacy Forge handshake indicator).
    String cleaned = hostname;
    int zeroIdx = cleaned.indexOf('\0');
    if (zeroIdx > -1) {
      cleaned = hostname.substring(0, zeroIdx);
    }

    // If we connect through an SRV record, there will be a period at the end (DNS usually elides
    // this ending octet).
    if (!cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) == '.') {
      cleaned = cleaned.substring(0, cleaned.length() - 1);
    }
    return cleaned;
  }

  @Override
  public void handleGeneric(MinecraftPacket packet) {
    if (hold(packet)) {
      return;
    }
    // Unknown packet received. Better to close the connection.
    connection.close(true);
  }

  @Override
  public void handleUnknown(ByteBuf buf) {
    if (hold(buf)) {
      return;
    }
    // Unknown packet received. Better to close the connection.
    connection.close(true);
  }

  @Override
  public void disconnected() {
    final Queue<Object> held = heldPackets;
    heldPackets = null;
    releaseHeldPackets(held);
  }

  @Override
  public String toString() {
    final boolean isPlayerAddressLoggingEnabled = connection.server.getConfiguration()
            .isPlayerAddressLoggingEnabled();
    final String playerIp =
            isPlayerAddressLoggingEnabled
                    ? this.connection.getRemoteAddress().toString() : "<ip address withheld>";
    return "[initial connection] " + playerIp;
  }

  private record LegacyInboundConnection(
          MinecraftConnection connection,
          LegacyPingPacket ping
  ) implements VelocityInboundConnection {

    @Override
    public InetSocketAddress getRemoteAddress() {
      return (InetSocketAddress) connection.getRemoteAddress();
    }

    @Override
    public Optional<InetSocketAddress> getVirtualHost() {
      return Optional.ofNullable(ping.getVhost());
    }

    @Override
    public Optional<String> getRawVirtualHost() {
      return getVirtualHost().map(InetSocketAddress::getHostName);
    }

    @Override
    public boolean isActive() {
      return !connection.isClosed();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
      return ProtocolVersion.LEGACY;
    }

    @Override
    public @NotNull String toString() {
      boolean isPlayerAddressLoggingEnabled = connection.server.getConfiguration().isPlayerAddressLoggingEnabled();
      String playerIp = isPlayerAddressLoggingEnabled ? this.getRemoteAddress().toString() : "<ip address withheld>";
      return "[legacy connection] " + playerIp;
    }

    @Override
    public MinecraftConnection getConnection() {
      return connection;
    }

    @Override
    public ProtocolState getProtocolState() {
      return connection.getState().toProtocolState();
    }

    @Override
    public HandshakeIntent getHandshakeIntent() {
      return HandshakeIntent.STATUS;
    }
  }
}
