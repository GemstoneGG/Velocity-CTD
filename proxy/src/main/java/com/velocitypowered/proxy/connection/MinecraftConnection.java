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

package com.velocitypowered.proxy.connection;

import static com.velocitypowered.proxy.network.Connections.CIPHER_DECODER;
import static com.velocitypowered.proxy.network.Connections.CIPHER_ENCODER;
import static com.velocitypowered.proxy.network.Connections.COMPRESSION_DECODER;
import static com.velocitypowered.proxy.network.Connections.COMPRESSION_ENCODER;
import static com.velocitypowered.proxy.network.Connections.FRAME_DECODER;
import static com.velocitypowered.proxy.network.Connections.FRAME_ENCODER;
import static com.velocitypowered.proxy.network.Connections.MINECRAFT_DECODER;
import static com.velocitypowered.proxy.network.Connections.MINECRAFT_ENCODER;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.encryption.VelocityCipher;
import com.velocitypowered.natives.encryption.VelocityCipherFactory;
import com.velocitypowered.natives.util.Natives;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.client.HandshakeSessionHandler;
import com.velocitypowered.proxy.connection.client.InitialInboundConnection;
import com.velocitypowered.proxy.connection.client.InitialLoginSessionHandler;
import com.velocitypowered.proxy.connection.client.StatusSessionHandler;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.VelocityConnectionEvent;
import com.velocitypowered.proxy.protocol.netty.MinecraftCipherDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftCipherEncoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftCompressDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftCompressorAndLengthEncoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintFrameDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintLengthEncoder;
import com.velocitypowered.proxy.protocol.netty.PlayPacketQueueInboundHandler;
import com.velocitypowered.proxy.protocol.netty.PlayPacketQueueOutboundHandler;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.SetCompressionPacket;
import com.velocitypowered.proxy.util.ClosestLocaleMatcher;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * A utility class to make working with the pipeline a little less painful and transparently handles
 * certain Minecraft protocol mechanics.
 */
public class MinecraftConnection extends ChannelInboundHandlerAdapter {

  /**
   * The logger instance used to report events and exceptions on this connection.
   */
  private static final Logger logger = LogManager.getLogger(MinecraftConnection.class);

  /**
   * The maximum size in bytes for an incoming packet from the client before disconnection.
   *
   * <p>This value is configurable via the {@code velocity.max-client-packet-size} system property.</p>
   * Defaults to {@code 2097152} (2 MiB).
   */
  public static final int MAX_CLIENT_PACKET_SIZE = Integer.getInteger("velocity.max-client-packet-size", 2097152);

  /**
   * The underlying Netty channel backing this Minecraft connection.
   */
  private final Channel channel;

  /**
   * Whether the client is currently undergoing a configuration state switch,
   * triggered by the server sending a {@code StartUpdatePacket}.
   */
  public boolean pendingConfigurationSwitch = false;

  /**
   * The remote address of the client, possibly overridden by HAProxy data.
   */
  private SocketAddress remoteAddress;

  /**
   * The current protocol state of this connection (e.g., HANDSHAKE, PLAY).
   */
  private StateRegistry state;

  /**
   * The session handlers associated with each state in the connection lifecycle.
   */
  private Map<StateRegistry, MinecraftSessionHandler> sessionHandlers;

  /**
   * The currently active session handler responsible for processing packets in the current state.
   */
  private @Nullable MinecraftSessionHandler activeSessionHandler;

  /**
   * The negotiated or forced Minecraft protocol version for this connection.
   */
  private ProtocolVersion protocolVersion;

  /**
   * The logical association of this connection, such as a player or server.
   */
  private @Nullable MinecraftConnectionAssociation association;

  /**
   * The Velocity server instance that owns and manages this connection.
   */
  public final VelocityServer server;

  /**
   * The connection type detected for this connection, such as vanilla or Forge.
   */
  private ConnectionType connectionType = ConnectionTypes.UNDETERMINED;

  /**
   * Whether the connection was closed due to a known (intentional) disconnect,
   * such as a normal logout or a server-initiated kick.
   */
  private boolean knownDisconnect = false;

  /**
   * Initializes a new {@link MinecraftConnection} instance.
   *
   * @param channel the channel on the connection
   * @param server  the Velocity instance
   */
  public MinecraftConnection(final Channel channel, final VelocityServer server) {
    this.channel = channel;
    this.remoteAddress = channel.remoteAddress();
    this.server = server;
    this.state = StateRegistry.HANDSHAKE;

    this.sessionHandlers = new HashMap<>();
  }

  /**
   * Called when the channel becomes active.
   *
   * <p>Subclasses overriding this method should ensure they invoke
   * {@code super.channelActive(ctx)} to preserve connection initialization behavior.</p>
   *
   * @param ctx the {@link ChannelHandlerContext} associated with this handler
   */
  @Override
  public void channelActive(final @NotNull ChannelHandlerContext ctx) {
    if (activeSessionHandler != null) {
      activeSessionHandler.connected();
    }

    if (association != null && server.getConfiguration().isLogPlayerConnections()) {
      logger.info("{} has connected", association);
    }
  }

  /**
   * Called when the channel becomes inactive.
   *
   * <p>Subclasses overriding this method should invoke
   * {@code super.channelInactive(ctx)} to ensure cleanup and disconnect logging occur properly.</p>
   *
   * @param ctx the {@link ChannelHandlerContext} associated with this handler
   */
  @Override
  public void channelInactive(final @NotNull ChannelHandlerContext ctx) {
    if (activeSessionHandler != null) {
      activeSessionHandler.disconnected();
    }

    if (association != null && !knownDisconnect
        && !(activeSessionHandler instanceof StatusSessionHandler)
        && (!(association instanceof InitialInboundConnection)
        || server.getConfiguration().isLogOfflineConnections())) {

      if (server.getConfiguration().isLogPlayerDisconnections()) {
        logger.info("{} has disconnected", association);
      }
    }
  }

  /**
   * Called for each message read from the channel.
   *
   * <p>Subclasses may override this method to intercept inbound traffic. If doing so,
   * they must call {@code super.channelRead(ctx, msg)} unless they fully replace
   * all handling logic.</p>
   *
   * @param ctx the {@link ChannelHandlerContext} associated with this handler
   * @param msg the inbound message to process
   */
  @Override
  public void channelRead(final @NotNull ChannelHandlerContext ctx, final @NotNull Object msg) {
    try {
      if (activeSessionHandler == null) {
        // No session handler available, do nothing
        return;
      }

      if (activeSessionHandler.beforeHandle()) {
        return;
      }

      if (this.isClosed()) {
        return;
      }

      switch (msg) {
        case MinecraftPacket pkt -> {
          if (!pkt.handle(activeSessionHandler)) {
            activeSessionHandler.handleGeneric(pkt);
          }
        }
        case HAProxyMessage proxyMessage -> this.remoteAddress = new InetSocketAddress(proxyMessage.sourceAddress(),
            proxyMessage.sourcePort());
        case ByteBuf buf -> {
          if (activeSessionHandler instanceof ClientPlaySessionHandler) {
            if (MAX_CLIENT_PACKET_SIZE > 0 && buf.readableBytes() > MAX_CLIENT_PACKET_SIZE) {
              logger.error("{}: received oversized packet ({} bytes > {} byte limit)", association, buf.readableBytes(), MAX_CLIENT_PACKET_SIZE);
              Component translated = GlobalTranslator.render(Component.translatable("velocity.kick.oversized-packet"),
                  ClosestLocaleMatcher.INSTANCE.lookupClosest(Locale.getDefault()));
              closeWith(DisconnectPacket.create(translated, getProtocolVersion(), getState()));
              return;
            }
          }

          activeSessionHandler.handleUnknown(buf);
        }
        default -> {
            // Do nothing, unknown handler
        }
      }
    } finally {
      ReferenceCountUtil.release(msg);
    }
  }

  /**
   * Called when the last read operation on the channel is completed.
   *
   * <p>Subclasses overriding this method should invoke
   * {@code super.channelReadComplete(ctx)} to preserve internal read-completion logic.</p>
   *
   * @param ctx the {@link ChannelHandlerContext} for this handler
   */
  @Override
  public void channelReadComplete(final ChannelHandlerContext ctx) {
    if (activeSessionHandler != null) {
      activeSessionHandler.readCompleted();
    }
  }

  /**
   * Called when an exception is raised during channel operations.
   *
   * <p>Subclasses may override to implement custom error handling, but should
   * invoke {@code super.exceptionCaught(ctx, cause)} unless the default behavior
   * is to be completely suppressed.</p>
   *
   * @param ctx the {@link ChannelHandlerContext}
   * @param cause the {@link Throwable} that was caught
   */
  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    if (ctx.channel().isActive()) {
      if (activeSessionHandler != null) {
        try {
          activeSessionHandler.exception(cause);
        } catch (Exception ex) {
          logger.error("{}: exception handling exception in {}",
              (association != null ? association : channel.remoteAddress()), activeSessionHandler, cause);
        }
      }

      if (association != null) {
        if (cause instanceof ReadTimeoutException) {
          if (server.getConfiguration().isLogOfflineConnections()
                  || !(association instanceof InitialInboundConnection)) {
            logger.error("{}: read timed out", association);
          }
        } else {
          boolean frontlineHandler = activeSessionHandler instanceof InitialLoginSessionHandler
              || activeSessionHandler instanceof HandshakeSessionHandler
              || activeSessionHandler instanceof StatusSessionHandler;
          boolean isQuietDecoderException = cause instanceof QuietDecoderException;
          boolean willLog = !isQuietDecoderException && !frontlineHandler;
          if (willLog) {
            logger.atError().withThrowable(cause)
                .log("{}: exception encountered in {}", association, activeSessionHandler);
          } else {
            knownDisconnect = true;
          }
        }
      }

      ctx.close();
    }
  }

  /**
   * Called when the writability of the channel changes.
   *
   * <p>Subclasses should invoke {@code super.channelWritabilityChanged(ctx)} if they override
   * this method to preserve session handler integration.</p>
   *
   * @param ctx the {@link ChannelHandlerContext} for this handler
   */
  @Override
  public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
    if (activeSessionHandler != null) {
      activeSessionHandler.writabilityChanged();
    }
  }

  private void ensureInEventLoop() {
    Preconditions.checkState(this.channel.eventLoop().inEventLoop(), "Not in event loop");
  }

  /**
   * Retrieves the Netty {@link EventLoop} assigned to this connection's channel.
   *
   * <p>This event loop is used for scheduling tasks and ensuring execution on the same thread
   * as the connection's pipeline.</p>
   *
   * @return the Netty {@link EventLoop} associated with this connection
   */
  public EventLoop eventLoop() {
    return channel.eventLoop();
  }

  /**
   * Writes and immediately flushes a message to the connection.
   *
   * @param msg the message to write
   * @return A {@link ChannelFuture} that will complete when a packet is successfully sent
   */
  @Nullable
  public ChannelFuture write(final Object msg) {
    if (channel.isActive()) {
      return channel.writeAndFlush(msg, channel.newPromise());
    } else {
      ReferenceCountUtil.release(msg);
      return null;
    }
  }

  /**
   * Writes, but does not flush, a message to the connection.
   *
   * @param msg the message to write
   */
  public void delayedWrite(final Object msg) {
    if (channel.isActive()) {
      channel.write(msg, channel.voidPromise());
    } else {
      ReferenceCountUtil.release(msg);
    }
  }

  /**
   * Flushes the connection.
   */
  public void flush() {
    if (channel.isActive()) {
      channel.flush();
    }
  }

  /**
   * Closes the connection after writing the {@code msg}.
   *
   * @param msg the message to write
   */
  public void closeWith(final Object msg) {
    if (channel.isActive()) {
      boolean is17 = this.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_8)
          && this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_7_2);
      if (is17 && this.getState() != StateRegistry.STATUS) {
        channel.eventLoop().execute(() -> {
          // 1.7.x versions have a race condition with switching protocol states, so explicitly
          // close the connection after a short while.
          this.setAutoReading(false);
          channel.eventLoop().schedule(() -> {
            knownDisconnect = true;
            channel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE);
          }, 250, TimeUnit.MILLISECONDS);
        });
      } else {
        knownDisconnect = true;
        channel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE);
      }
    }
  }

  /**
   * Closes the connection and marks the disconnect as known.
   *
   * <p>This is equivalent to calling {@link #close(boolean)} with {@code true}, indicating that
   * the disconnect was expected (e.g., player quit or server-initiated).</p>
   */
  public void close() {
    close(true);
  }

  /**
   * Immediately closes the connection.
   *
   * @param markKnown whether the disconnection is known
   */
  public void close(final boolean markKnown) {
    if (channel.isActive()) {
      if (channel.eventLoop().inEventLoop()) {
        if (markKnown) {
          knownDisconnect = true;
        }

        channel.close();
      } else {
        channel.eventLoop().execute(() -> {
          if (markKnown) {
            knownDisconnect = true;
          }
          channel.close();
        });
      }
    }
  }

  /**
   * Retrieves the Netty {@link Channel} backing this Minecraft connection.
   *
   * @return the underlying {@link Channel}
   */
  public Channel getChannel() {
    return channel;
  }

  /**
   * Determines whether the connection has been closed.
   *
   * @return {@code true} if the connection is closed, otherwise {@code false}
   */
  public boolean isClosed() {
    return !channel.isActive();
  }

  /**
   * Gets the remote {@link SocketAddress} of the client. This may be overridden by a
   * {@link io.netty.handler.codec.haproxy.HAProxyMessage}.
   *
   * @return the client's remote address
   */
  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  /**
   * Gets the current {@link StateRegistry} associated with this connection.
   *
   * @return the connection's current protocol state
   */
  public StateRegistry getState() {
    return state;
  }

  /**
   * Returns whether Netty's {@code autoRead} is enabled for this channel.
   * This indicates whether inbound data will be read automatically.
   *
   * @return {@code true} if auto-reading is enabled, otherwise {@code false}
   */
  public boolean isAutoReading() {
    return channel.config().isAutoRead();
  }

  /**
   * Returns whether the disconnection was expected and initiated
   * by the proxy or server intentionally (i.e. not due to a crash or timeout).
   *
   * @return {@code true} if the disconnection was known and intentional
   */
  public boolean isKnownDisconnect() {
    return knownDisconnect;
  }

  /**
   * Determines whether the channel should continue reading data automatically.
   *
   * @param autoReading whether we should read data automatically
   */
  public void setAutoReading(final boolean autoReading) {
    ensureInEventLoop();

    channel.config().setAutoRead(autoReading);
    if (autoReading) {
      // For some reason, the channel may not completely read its queued contents once autoread
      // is turned back on, even though toggling autoreading on should handle things automatically.
      // We will issue an explicit read after turning on autoread.
      //
      // Many thanks to @creeper123123321.
      channel.read();
    }
  }

  // Ideally only used by the state switch

  /**
   * Sets the new state for the connection.
   *
   * @param state the state to use
   */
  public void setState(final StateRegistry state) {
    ensureInEventLoop();

    this.state = state;
    final MinecraftVarintFrameDecoder frameDecoder = this.channel.pipeline()
        .get(MinecraftVarintFrameDecoder.class);
    if (frameDecoder != null) {
      frameDecoder.setState(state);
    }
    // If the connection is LEGACY (<1.6), the decoder and encoder are not set.
    final MinecraftEncoder minecraftEncoder = this.channel.pipeline()
        .get(MinecraftEncoder.class);
    if (minecraftEncoder != null) {
      minecraftEncoder.setState(state);
    }
    final MinecraftDecoder minecraftDecoder = this.channel.pipeline()
        .get(MinecraftDecoder.class);
    if (minecraftDecoder != null) {
      minecraftDecoder.setState(state);
    }

    if (state == StateRegistry.CONFIG) {
      // Activate the play packet queue
      addPlayPacketQueueHandler();
    } else {
      // Remove the queue
      if (this.channel.pipeline().get(Connections.PLAY_PACKET_QUEUE_OUTBOUND) != null) {
        this.channel.pipeline().remove(Connections.PLAY_PACKET_QUEUE_OUTBOUND);
      }
      if (this.channel.pipeline().get(Connections.PLAY_PACKET_QUEUE_INBOUND) != null) {
        this.channel.pipeline().remove(Connections.PLAY_PACKET_QUEUE_INBOUND);
      }
    }
  }

  /**
   * Adds the play packet queue handler.
   */
  public void addPlayPacketQueueHandler() {
    if (this.channel.pipeline().get(Connections.PLAY_PACKET_QUEUE_OUTBOUND) == null) {
      this.channel.pipeline().addAfter(Connections.MINECRAFT_ENCODER, Connections.PLAY_PACKET_QUEUE_OUTBOUND,
           new PlayPacketQueueOutboundHandler(this.protocolVersion, channel.pipeline().get(MinecraftEncoder.class).getDirection()));
    }
    if (this.channel.pipeline().get(Connections.PLAY_PACKET_QUEUE_INBOUND) == null) {
      this.channel.pipeline().addAfter(Connections.MINECRAFT_DECODER, Connections.PLAY_PACKET_QUEUE_INBOUND,
           new PlayPacketQueueInboundHandler(this.protocolVersion, channel.pipeline().get(MinecraftDecoder.class).getDirection()));
    }
  }

  /**
   * Returns the protocol version negotiated or assigned to this connection.
   *
   * @return the {@link ProtocolVersion} associated with this connection
   */
  public ProtocolVersion getProtocolVersion() {
    return protocolVersion;
  }

  /**
   * Sets the new protocol version for the connection.
   *
   * @param protocolVersion the protocol version to use
   */
  public void setProtocolVersion(final ProtocolVersion protocolVersion) {
    ensureInEventLoop();

    boolean changed = this.protocolVersion != protocolVersion;
    this.protocolVersion = protocolVersion;
    if (protocolVersion != ProtocolVersion.LEGACY) {
      this.channel.pipeline().get(MinecraftEncoder.class).setProtocolVersion(protocolVersion);
      this.channel.pipeline().get(MinecraftDecoder.class).setProtocolVersion(protocolVersion);
    } else {
      // Legacy handshake handling
      this.channel.pipeline().remove(MINECRAFT_ENCODER);
      this.channel.pipeline().remove(MINECRAFT_DECODER);
    }

    if (changed) {
      channel.pipeline().fireUserEventTriggered(VelocityConnectionEvent.PROTOCOL_VERSION_CHANGED);
    }
  }

  /**
   * Retrieves the currently active {@link MinecraftSessionHandler} responsible for handling
   * packets for this connection.
   *
   * @return the active session handler, or {@code null} if none is set
   */
  public @Nullable MinecraftSessionHandler getActiveSessionHandler() {
    return activeSessionHandler;
  }

  /**
   * Retrieves a registered {@link MinecraftSessionHandler} associated with the given protocol state.
   *
   * @param registry the protocol state for which the handler is queried
   * @return the session handler for the specified state, or {@code null} if not registered
   */
  public @Nullable MinecraftSessionHandler getSessionHandlerForRegistry(final StateRegistry registry) {
    return this.sessionHandlers.getOrDefault(registry, null);
  }

  /**
   * Sets the session handler for this connection.
   *
   * @param registry       the registry of the handler
   * @param sessionHandler the handler to use
   */
  public void setActiveSessionHandler(final StateRegistry registry,
                                      final MinecraftSessionHandler sessionHandler) {
    Preconditions.checkNotNull(registry);
    ensureInEventLoop();

    if (this.activeSessionHandler != null) {
      this.activeSessionHandler.deactivated();
    }

    this.sessionHandlers.put(registry, sessionHandler);
    this.activeSessionHandler = sessionHandler;
    setState(registry);
    sessionHandler.activated();
  }

  /**
   * Switches the active session handler to the respective registry one.
   *
   * @param registry the registry of the handler
   * @return true, if successful and handler is present
   */
  public boolean setActiveSessionHandler(final StateRegistry registry) {
    Preconditions.checkNotNull(registry);
    ensureInEventLoop();

    MinecraftSessionHandler handler = getSessionHandlerForRegistry(registry);
    if (handler != null) {
      boolean flag = true;
      if (this.activeSessionHandler != null) {
        flag = !Objects.equals(handler, this.activeSessionHandler);
        if (flag) {
          this.activeSessionHandler.deactivated();
        }
      }

      this.activeSessionHandler = handler;
      setState(registry);
      if (flag) {
        handler.activated();
      }
    }

    return handler != null;
  }

  /**
   * Adds a secondary session handler for this connection.
   *
   * @param registry       the registry of the handler
   * @param sessionHandler the handler to use
   */
  public void addSessionHandler(final StateRegistry registry, final MinecraftSessionHandler sessionHandler) {
    Preconditions.checkNotNull(registry);
    Preconditions.checkArgument(registry != state, "Handler would overwrite handler");
    ensureInEventLoop();

    this.sessionHandlers.put(registry, sessionHandler);
  }

  private void ensureOpen() {
    Preconditions.checkState(!isClosed(), "Connection is closed.");
  }

  /**
   * Sets the compression threshold on the connection. You are responsible for sending {@link
   * SetCompressionPacket} beforehand.
   *
   * @param threshold the compression threshold to use
   */
  public void setCompressionThreshold(final int threshold) {
    ensureOpen();
    ensureInEventLoop();

    if (threshold == -1) {
      final ChannelHandler removedDecoder = channel.pipeline().remove(COMPRESSION_DECODER);
      final ChannelHandler removedEncoder = channel.pipeline().remove(COMPRESSION_ENCODER);

      if (removedDecoder != null && removedEncoder != null) {
        channel.pipeline().addBefore(MINECRAFT_DECODER, FRAME_ENCODER,
            MinecraftVarintLengthEncoder.INSTANCE);
        channel.pipeline().fireUserEventTriggered(VelocityConnectionEvent.COMPRESSION_DISABLED);
      }
    } else {
      MinecraftCompressDecoder decoder = (MinecraftCompressDecoder) channel.pipeline()
          .get(COMPRESSION_DECODER);
      MinecraftCompressorAndLengthEncoder encoder =
          (MinecraftCompressorAndLengthEncoder) channel.pipeline().get(COMPRESSION_ENCODER);
      if (decoder != null && encoder != null) {
        decoder.setThreshold(threshold);
        encoder.setThreshold(threshold);
      } else {
        int level = server.getConfiguration().getCompressionLevel();
        VelocityCompressor compressor = Natives.compress.get().create(level);

        encoder = new MinecraftCompressorAndLengthEncoder(threshold, compressor);
        decoder = new MinecraftCompressDecoder(threshold, compressor);

        channel.pipeline().remove(FRAME_ENCODER);
        channel.pipeline().addBefore(MINECRAFT_DECODER, COMPRESSION_DECODER, decoder);
        channel.pipeline().addBefore(MINECRAFT_ENCODER, COMPRESSION_ENCODER, encoder);

        channel.pipeline().fireUserEventTriggered(VelocityConnectionEvent.COMPRESSION_ENABLED);
      }
    }
  }

  /**
   * Enables encryption on the connection.
   *
   * @param secret the secret key negotiated between the client and the server
   * @throws GeneralSecurityException if encryption can't be enabled
   */
  public void enableEncryption(final byte[] secret) throws GeneralSecurityException {
    ensureOpen();
    ensureInEventLoop();

    SecretKey key = new SecretKeySpec(secret, "AES");

    VelocityCipherFactory factory = Natives.cipher.get();
    VelocityCipher decryptionCipher = factory.forDecryption(key);
    VelocityCipher encryptionCipher = factory.forEncryption(key);
    channel.pipeline().addBefore(FRAME_DECODER, CIPHER_DECODER, new MinecraftCipherDecoder(decryptionCipher));
    channel.pipeline().addBefore(FRAME_ENCODER, CIPHER_ENCODER, new MinecraftCipherEncoder(encryptionCipher));

    channel.pipeline().fireUserEventTriggered(VelocityConnectionEvent.ENCRYPTION_ENABLED);
  }

  /**
   * Returns the current {@link MinecraftConnectionAssociation} associated with this connection.
   *
   * <p>This association typically represents a player or other connection-bound identity.
   *
   * @return the current connection association, or {@code null} if not yet associated
   */
  public @Nullable MinecraftConnectionAssociation getAssociation() {
    return association;
  }

  /**
   * Sets the {@link MinecraftConnectionAssociation} for this connection.
   *
   * <p>This method must be called from within the Netty event loop for this connection.
   *
   * @param association the association to set
   * @throws IllegalStateException if called outside the Netty event loop
   */
  public void setAssociation(final MinecraftConnectionAssociation association) {
    ensureInEventLoop();
    this.association = association;
  }

  /**
   * Gets the detected {@link ConnectionType}.
   *
   * @return The {@link ConnectionType}
   */
  public ConnectionType getType() {
    return connectionType;
  }

  /**
   * Sets the detected {@link ConnectionType}.
   *
   * @param connectionType The {@link ConnectionType}
   */
  public void setType(final ConnectionType connectionType) {
    this.connectionType = connectionType;
  }
}
