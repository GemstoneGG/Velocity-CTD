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
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes {@link MinecraftPacket} instances.
 */
public class MinecraftEncoder extends MessageToByteEncoder<MinecraftPacket> {

  /**
   * The direction this encoder is targeting.
   *
   * <p>This determines whether packets are being encoded for the
   * {@link ProtocolUtils.Direction#CLIENTBOUND} or {@link ProtocolUtils.Direction#SERVERBOUND}
   * direction.</p>
   */
  private final ProtocolUtils.Direction direction;

  /**
   * The current connection state this encoder is operating under.
   *
   * <p>This affects the set of packets that are valid to send, and how they
   * are serialized to the output buffer.</p>
   */
  private StateRegistry state;

  /**
   * The active protocol registry used for encoding packet IDs and resolving
   * version-specific serialization behaviors.
   *
   * <p>This registry is derived from the current {@link #state} and {@link #direction}
   * for a given {@link ProtocolVersion}.</p>
   */
  private StateRegistry.PacketRegistry.ProtocolRegistry registry;

  /**
   * Creates a new {@code MinecraftEncoder} encoding packets for the specified {@code direction}.
   *
   * @param direction the direction to encode to
   */
  public MinecraftEncoder(final ProtocolUtils.Direction direction) {
    this.direction = Preconditions.checkNotNull(direction, "direction");
    this.registry = StateRegistry.HANDSHAKE.getProtocolRegistry(direction, ProtocolVersion.MINIMUM_VERSION);
    this.state = StateRegistry.HANDSHAKE;
  }

  /**
   * Encodes a {@link MinecraftPacket} into its binary representation for transmission.
   *
   * <p>This method first writes the packet ID using VarInt encoding, then delegates
   * to the packet's {@link MinecraftPacket#encode(ByteBuf, ProtocolUtils.Direction, ProtocolVersion)}
   * method to write the packet-specific data.</p>
   *
   * @param ctx the Netty channel context
   * @param msg the Minecraft packet to encode
   * @param out the output buffer to write the encoded packet into
   * @throws RuntimeException if the packet is not registered in the current protocol registry
   */
  @Override
  protected void encode(final ChannelHandlerContext ctx, final MinecraftPacket msg, final ByteBuf out) {
    int packetId = this.registry.getPacketId(msg);
    ProtocolUtils.writeVarInt(out, packetId);
    msg.encode(out, direction, registry.version);
  }

  /**
   * Allocates a {@link ByteBuf} for encoding the specified packet.
   *
   * <p>This method provides an optimized buffer allocation by using the
   * packet's {@link MinecraftPacket#encodeSizeHint(ProtocolUtils.Direction, ProtocolVersion)}
   * as a preallocation estimate for the encoded size.</p>
   *
   * <p>If a size hint is available (i.e., greater than or equal to 0), the encoder
   * calculates the total buffer size as the sum of the packet ID’s VarInt length
   * and the packet’s estimated payload size. Otherwise, it defers to the superclass
   * allocation strategy.</p>
   *
   * @param ctx the Netty channel handler context
   * @param msg the Minecraft packet to encode
   * @param preferDirect whether to prefer a direct (off-heap) buffer
   * @return a {@link ByteBuf} pre-allocated to the estimated encoded packet size
   * @throws Exception if an error occurs during buffer allocation
   */
  @Override
  protected ByteBuf allocateBuffer(final ChannelHandlerContext ctx, final MinecraftPacket msg,
                                   final boolean preferDirect) throws Exception {
    int hint = msg.encodeSizeHint(direction, registry.version);
    if (hint < 0) {
      return super.allocateBuffer(ctx, msg, preferDirect);
    }

    int packetId = this.registry.getPacketId(msg);
    int totalHint = ProtocolUtils.varIntBytes(packetId) + hint;
    return preferDirect ? ctx.alloc().ioBuffer(totalHint) : ctx.alloc().heapBuffer(totalHint);
  }

  /**
   * Updates the protocol version used by this encoder.
   *
   * <p>This method re-initializes the protocol registry to match the given version
   * under the current {@link #state} and {@link #direction}.</p>
   *
   * @param protocolVersion the new protocol version to encode against
   */
  public void setProtocolVersion(final ProtocolVersion protocolVersion) {
    this.registry = state.getProtocolRegistry(direction, protocolVersion);
  }

  /**
   * Updates the connection state used by this encoder.
   *
   * <p>This method also resets the protocol registry using the current version from
   * the previous registry and the newly provided state.</p>
   *
   * @param state the new connection state
   */
  public void setState(final StateRegistry state) {
    this.state = state;
    this.setProtocolVersion(registry.version);
  }

  /**
   * Gets the direction this encoder is targeting.
   *
   * @return the encoder's target direction
   */
  public ProtocolUtils.Direction getDirection() {
    return direction;
  }
}
