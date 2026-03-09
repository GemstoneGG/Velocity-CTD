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

package com.velocitypowered.proxy.protocol.netty;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.util.except.QuietRuntimeException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.CorruptedFrameException;
import org.jetbrains.annotations.NotNull;

/**
 * Decodes Minecraft packets.
 */
public class MinecraftDecoder extends ChannelInboundHandlerAdapter {

  /**
   * Enables debug logging for packet decode failures.
   */
  public static final boolean DEBUG = Boolean.getBoolean("velocity.packet-decode-logging");

  /**
   * Shared quiet exception thrown when a packet decode fails and debug is disabled.
   */
  private static final QuietRuntimeException DECODE_FAILED =
      new QuietRuntimeException("A packet did not decode successfully (invalid data). For more "
          + "information, launch Velocity with -Dvelocity.packet-decode-logging=true to see more.");

  /**
   * The direction of the packet flow this decoder is handling.
   *
   * <p>This defines whether packets are being decoded in the {@code SERVERBOUND}
   * or {@code CLIENTBOUND} direction, and is used to resolve the correct
   * {@link StateRegistry.PacketRegistry} for decoding.</p>
   */
  private final ProtocolUtils.Direction direction;

  /**
   * The current connection state this decoder is operating under.
   *
   * <p>This state affects which packet types are expected and how they
   * are decoded. States typically include {@code HANDSHAKE}, {@code STATUS},
   * {@code LOGIN}, and {@code PLAY}.</p>
   */
  private StateRegistry state;

  /**
   * The active protocol registry for the current state and direction.
   *
   * <p>This registry provides packet ID mappings and decoder constructors
   * for the selected {@link ProtocolVersion} in the current {@link #state}
   * and {@link #direction}.</p>
   */
  private StateRegistry.PacketRegistry.ProtocolRegistry registry;

  /**
   * Creates a new {@code MinecraftDecoder} decoding packets from the specified {@code direction}.
   *
   * @param direction the direction from which we decode from
   */
  public MinecraftDecoder(final ProtocolUtils.Direction direction) {
    this.direction = Preconditions.checkNotNull(direction, "direction");
    this.registry = StateRegistry.HANDSHAKE.getProtocolRegistry(direction, ProtocolVersion.MINIMUM_VERSION);
    this.state = StateRegistry.HANDSHAKE;
  }

  /**
   * Handles inbound messages from the Netty pipeline.
   *
   * <p>If the message is a {@link ByteBuf}, it is treated as a raw Minecraft packet
   * and passed to {@link #tryDecode(ChannelHandlerContext, ByteBuf)} for decoding.
   * Otherwise, the message is forwarded through the pipeline unchanged.</p>
   *
   * @param ctx the Netty channel context
   * @param msg the inbound message to process
   * @throws Exception if an error occurs during decoding
   */
  @Override
  public void channelRead(final @NotNull ChannelHandlerContext ctx, final @NotNull Object msg) throws Exception {
    if (msg instanceof ByteBuf buf) {
      tryDecode(ctx, buf);
    } else {
      ctx.fireChannelRead(msg);
    }
  }

  private void tryDecode(final ChannelHandlerContext ctx, final ByteBuf buf) throws Exception {
    if (!ctx.channel().isActive() || !buf.isReadable()) {
      buf.release();
      return;
    }

    int originalReaderIndex = buf.readerIndex();
    int packetId = ProtocolUtils.readVarInt(buf);
    MinecraftPacket packet = this.registry.createPacket(packetId);
    if (packet == null) {
      buf.readerIndex(originalReaderIndex);
      ctx.fireChannelRead(buf);
    } else {
      try {
        doLengthSanityChecks(buf, packet);

        try {
          packet.decode(buf, direction, registry.version);
        } catch (Exception e) {
          throw handleDecodeFailure(e, packet, packetId);
        }

        if (buf.isReadable()) {
          throw handleOverflow(packet, buf.readerIndex(), buf.writerIndex());
        }

        ctx.fireChannelRead(packet);
      } finally {
        buf.release();
      }
    }
  }

  private void doLengthSanityChecks(final ByteBuf buf, final MinecraftPacket packet) throws Exception {
    int expectedMinLen = packet.decodeExpectedMinLength(buf, direction, registry.version);
    int expectedMaxLen = packet.decodeExpectedMaxLength(buf, direction, registry.version);
    if (expectedMaxLen != -1 && buf.readableBytes() > expectedMaxLen) {
      throw handleOverflow(packet, expectedMaxLen, buf.readableBytes());
    }

    if (buf.readableBytes() < expectedMinLen) {
      throw handleUnderflow(packet, expectedMaxLen, buf.readableBytes());
    }
  }

  private Exception handleOverflow(final MinecraftPacket packet, final int expected, final int actual) {
    if (DEBUG) {
      return new CorruptedFrameException("Packet sent for " + packet.getClass() + " was too "
          + "big (expected " + expected + " bytes, got " + actual + " bytes)");
    } else {
      return DECODE_FAILED;
    }
  }

  private Exception handleUnderflow(final MinecraftPacket packet, final int expected, final int actual) {
    if (DEBUG) {
      return new CorruptedFrameException("Packet sent for " + packet.getClass() + " was too "
          + "small (expected " + expected + " bytes, got " + actual + " bytes)");
    } else {
      return DECODE_FAILED;
    }
  }

  private Exception handleDecodeFailure(final Exception cause, final MinecraftPacket packet, final int packetId) {
    if (DEBUG) {
      return new CorruptedFrameException(
          "Error decoding " + packet.getClass() + " " + getExtraConnectionDetail(packetId), cause);
    } else {
      return DECODE_FAILED;
    }
  }

  private String getExtraConnectionDetail(final int packetId) {
    return "Direction " + direction + " Protocol " + registry.version + " State " + state
        + " ID 0x" + Integer.toHexString(packetId);
  }

  /**
   * Sets the protocol version used to look up packet codecs.
   *
   * @param protocolVersion the new protocol version
   */
  public void setProtocolVersion(final ProtocolVersion protocolVersion) {
    this.registry = state.getProtocolRegistry(direction, protocolVersion);
  }

  /**
   * Sets the current protocol state and updates the packet registry.
   *
   * @param state the new connection state
   */
  public void setState(final StateRegistry state) {
    this.state = state;
    this.setProtocolVersion(registry.version);
  }

  /**
   * Gets the packet direction handled by this decoder.
   *
   * @return the decode direction
   */
  public ProtocolUtils.Direction getDirection() {
    return direction;
  }
}
