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

import static com.velocitypowered.proxy.protocol.netty.MinecraftVarintLengthEncoder.IS_JAVA_CIPHER;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.data.UncompressedPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;

/**
 * Encodes {@link MinecraftPacket} instances into {@link UncompressedPacket}s so the downstream
 * {@link MinecraftCompressorAndLengthEncoder}.
 */
public class MinecraftEncoder extends MessageToMessageEncoder<MinecraftPacket> {

  private final ProtocolUtils.Direction direction;

  private StateRegistry state;

  private StateRegistry.PacketRegistry.ProtocolRegistry registry;

  /**
   * Creates a new {@code MinecraftEncoder} encoding packets for the specified {@code direction}.
   *
   * @param direction the direction to encode to
   */
  public MinecraftEncoder(ProtocolUtils.Direction direction) {
    this.direction = Preconditions.checkNotNull(direction, "direction");
    this.registry = StateRegistry.HANDSHAKE.getProtocolRegistry(direction, ProtocolVersion.MINIMUM_VERSION);
    this.state = StateRegistry.HANDSHAKE;
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, MinecraftPacket msg, List<Object> out) {
    int packetId = this.registry.getPacketId(msg);
    ByteBuf buf = allocateBuffer(ctx, msg, packetId);
    boolean success = false;
    try {
      ProtocolUtils.writeVarInt(buf, packetId);
      msg.encode(buf, direction, registry.version);
      out.add(new UncompressedPacket(packetId, buf));
      success = true;
    } finally {
      if (!success) {
        buf.release();
      }
    }
  }

  private ByteBuf allocateBuffer(ChannelHandlerContext ctx, MinecraftPacket msg, int packetId) {
    int hint = msg.encodeSizeHint(direction, registry.version);
    if (hint < 0) {
      return IS_JAVA_CIPHER ? ctx.alloc().heapBuffer() : ctx.alloc().ioBuffer();
    }
    int totalHint = ProtocolUtils.varIntBytes(packetId) + hint;
    return IS_JAVA_CIPHER ? ctx.alloc().heapBuffer(totalHint) : ctx.alloc().ioBuffer(totalHint);
  }

  public void setProtocolVersion(ProtocolVersion protocolVersion) {
    this.registry = state.getProtocolRegistry(direction, protocolVersion);
  }

  public void setState(StateRegistry state) {
    this.state = state;
    this.setProtocolVersion(registry.version);
  }

  public ProtocolUtils.Direction getDirection() {
    return direction;
  }
}
