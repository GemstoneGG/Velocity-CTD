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

package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.util.DeferredByteBufHolder;
import io.netty.buffer.ByteBuf;

/**
 * Represents a serverbound packet carrying an opaque custom click action payload.
 *
 * <p>The payload is retained as-is and forwarded to the session handler without
 * interpretation by the proxy.</p>
 */
public class ServerboundCustomClickActionPacket extends DeferredByteBufHolder implements MinecraftPacket {

  /**
   * Creates a new {@link ServerboundCustomClickActionPacket} with no initial content.
   */
  public ServerboundCustomClickActionPacket() {
    super(null);
  }

  /**
   * Decodes this packet from the given buffer.
   *
   * <p>All remaining readable bytes are retained as the packet payload.</p>
   *
   * @param buf the buffer to read from
   * @param direction the protocol direction
   * @param version the protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    replace(buf.readRetainedSlice(buf.readableBytes()));
  }

  /**
   * Encodes this packet to the given buffer.
   *
   * <p>The stored payload bytes are written verbatim.</p>
   *
   * @param buf the buffer to write to
   * @param direction the protocol direction
   * @param version the protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    buf.writeBytes(content());
  }

  /**
   * Dispatches this packet to the provided session handler.
   *
   * @param handler the session handler
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
