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

package com.velocitypowered.proxy.protocol.packet.config;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.protocol.util.DeferredByteBufHolder;
import io.netty.buffer.ByteBuf;

/**
 * The {@code RegistrySyncPacket} class is responsible for synchronizing registry data
 * between the server and client in Minecraft.
 *
 * <p>This packet is used to ensure that the client has the same registry information as
 * the server, covering aspects like blocks, items, entities, and other game elements
 * that are part of Minecraft's internal registries.</p>
 *
 * <p>It extends the {@link DeferredByteBufHolder} class to handle deferred buffering
 * operations for potentially large sets of registry data, which may include
 * complex serialization processes.</p>
 */
public class RegistrySyncPacket extends DeferredByteBufHolder implements MinecraftPacket {

  /**
   * Constructs a new empty {@code RegistrySyncPacket} with a {@code null} buffer reference.
   */
  public RegistrySyncPacket() {
    super(null);
  }

  /**
   * Decodes this registry sync packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the remaining bytes into the internal content buffer.</p>
   *
   * <p><strong>Note:</strong> Due to changes in the NBT format introduced in Minecraft 1.20.2,
   * parsing this packet is non-trivial. As such, the content is stored in raw form
   * without deserialization.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    this.replace(buf.readRetainedSlice(buf.readableBytes()));
  }

  /**
   * Encodes this registry sync packet into the given {@link ByteBuf}.
   *
   * <p>This writes the content buffer containing the serialized registry NBT data
   * to the outgoing stream.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    buf.writeBytes(content());
  }

  /**
   * Handles this registry sync packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} so that the server
   * can synchronize registry data with the client session.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Provides an estimated number of bytes required to encode this registry sync packet.
   *
   * <p>Because this packet consists entirely of pre-serialized registry data stored in its
   * internal buffer, the encoded size is exactly equal to the number of readable bytes in
   * that buffer. This estimate allows the encoder to preallocate an appropriately sized
   * output buffer to avoid resizing during transmission.</p>
   *
   * @param direction the packet direction (clientbound or serverbound)
   * @param version the Minecraft protocol version
   * @return the exact number of readable bytes in the content buffer
   */
  @Override
  public int encodeSizeHint(final Direction direction, final ProtocolVersion version) {
    return content().readableBytes();
  }
}
