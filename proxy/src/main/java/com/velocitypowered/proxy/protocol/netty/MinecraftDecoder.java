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

package com.velocitypowered.proxy.protocol.netty;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.config.StartUpdatePacket;
import com.velocitypowered.proxy.util.except.QuietRuntimeException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.CorruptedFrameException;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Decodes Minecraft packets.
 */
public class MinecraftDecoder extends ChannelInboundHandlerAdapter {

  public static final boolean DEBUG = Boolean.getBoolean("velocity.packet-decode-logging");
  private static final QuietRuntimeException DECODE_FAILED =
      new QuietRuntimeException("A packet did not decode successfully (invalid data). For more "
          + "information, launch Velocity with -Dvelocity.packet-decode-logging=true to see more.");

  private final ProtocolUtils.Direction direction;
  private StateRegistry state;
  private StateRegistry.PacketRegistry.ProtocolRegistry registry;
  private @Nullable StateRegistry awaitedState;
  private int awaitedFirstPacketId;

  /**
   * Creates a new {@code MinecraftDecoder} decoding packets from the specified {@code direction}.
   *
   * @param direction the direction from which we decode from
   */
  public MinecraftDecoder(ProtocolUtils.Direction direction) {
    this.direction = Preconditions.checkNotNull(direction, "direction");
    this.registry = StateRegistry.HANDSHAKE.getProtocolRegistry(
        direction, ProtocolVersion.MINIMUM_VERSION);
    this.state = StateRegistry.HANDSHAKE;
  }

  @Override
  public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
    if (msg instanceof ByteBuf buf) {
      try {
        tryDecode(ctx, buf);
      } finally {
        buf.release();
      }
    } else {
      ctx.fireChannelRead(msg);
    }
  }

  private void tryDecode(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
    if (!ctx.channel().isActive() || !buf.isReadable()) {
      return;
    }

    int originalReaderIndex = buf.readerIndex();
    int packetId = ProtocolUtils.readVarInt(buf);
    if (this.awaitedState != null) {
      if (packetId == this.awaitedFirstPacketId) {
        setState(this.awaitedState);
      } else if (!(this.registry.createPacket(packetId) instanceof DisconnectPacket)) {
        return;
      }
    }
    MinecraftPacket packet = this.registry.createPacket(packetId);
    if (packet == null) {
      buf.readerIndex(originalReaderIndex);
      if (this.direction == ProtocolUtils.Direction.SERVERBOUND && this.state != StateRegistry.PLAY) {
        throw this.handleInvalidPacketId(packetId);
      }
      ctx.fireChannelRead(buf.retain());
    } else {
      doLengthSanityChecks(buf, packet);

      try {
        packet.decode(buf, direction, registry.version);
      } catch (Exception e) {
        throw handleDecodeFailure(e, packet, packetId);
      }

      if (buf.isReadable()) {
        throw handleOverflow(packet, buf.readerIndex(), buf.writerIndex());
      }
      if (packet instanceof StartUpdatePacket && direction == ProtocolUtils.Direction.CLIENTBOUND) {
        // The backend switches to CONFIG as soon as it sends this, without waiting for the
        // acknowledgement, so switch here rather than in the session handler: a paused connection
        // holds this packet while the ones behind it are decoded.
        setState(StateRegistry.CONFIG);
      }
      ctx.fireChannelRead(packet);
    }
  }

  private void doLengthSanityChecks(ByteBuf buf, MinecraftPacket packet) throws Exception {
    int expectedMinLen = packet.decodeExpectedMinLength(buf, direction, registry.version);
    int expectedMaxLen = packet.decodeExpectedMaxLength(buf, direction, registry.version);
    if (expectedMaxLen != -1 && buf.readableBytes() > expectedMaxLen) {
      throw handleOverflow(packet, expectedMaxLen, buf.readableBytes());
    }
    if (buf.readableBytes() < expectedMinLen) {
      throw handleUnderflow(packet, expectedMinLen, buf.readableBytes());
    }
  }

  private Exception handleOverflow(MinecraftPacket packet, int expected, int actual) {
    if (DEBUG) {
      return new CorruptedFrameException("Packet sent for " + packet.getClass() + " was too "
          + "big (expected " + expected + " bytes, got " + actual + " bytes)");
    } else {
      return DECODE_FAILED;
    }
  }

  private Exception handleUnderflow(MinecraftPacket packet, int expected, int actual) {
    if (DEBUG) {
      return new CorruptedFrameException("Packet sent for " + packet.getClass() + " was too "
          + "small (expected " + expected + " bytes, got " + actual + " bytes)");
    } else {
      return DECODE_FAILED;
    }
  }

  private Exception handleDecodeFailure(Exception cause, MinecraftPacket packet, int packetId) {
    if (DEBUG) {
      return new CorruptedFrameException(
          "Error decoding " + packet.getClass() + " " + getExtraConnectionDetail(packetId), cause);
    } else {
      return DECODE_FAILED;
    }
  }

  private Exception handleInvalidPacketId(int packetId) {
    if (DEBUG) {
      return new CorruptedFrameException("Invalid packet " + getExtraConnectionDetail(packetId));
    } else {
      return DECODE_FAILED;
    }
  }

  private String getExtraConnectionDetail(int packetId) {
    return "Direction " + direction + " Protocol " + registry.version + " State " + state
        + " ID 0x" + Integer.toHexString(packetId);
  }

  public void setProtocolVersion(ProtocolVersion protocolVersion) {
    this.registry = state.getProtocolRegistry(direction, protocolVersion);
  }

  public void setState(StateRegistry state) {
    this.state = state;
    this.awaitedState = null;
    this.setProtocolVersion(registry.version);
  }

  /**
   * Keeps decoding in {@code current} until the peer's first packet in {@code next} arrives, and
   * switches then. A peer asked to change state does so only once it reads the request, so what it
   * sent before that is still in {@code current} and was meant for a state it has left: a
   * disconnect among it is passed on, anything else is dropped (a keepalive answered now would be
   * refused by the peer's new state).
   *
   * @param current the state the peer is still sending in
   * @param next the state the peer was asked to switch to
   * @param firstPacketId the ID, in {@code next}, of the first packet the peer sends there
   */
  public void awaitState(StateRegistry current, StateRegistry next, int firstPacketId) {
    setState(current);
    this.awaitedState = next;
    this.awaitedFirstPacketId = firstPacketId;
  }

  public ProtocolUtils.Direction getDirection() {
    return direction;
  }
}
