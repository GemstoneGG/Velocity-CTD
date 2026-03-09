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

package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * Represents a legacy handshake packet in Minecraft, which is typically used
 * during the initial connection process for older versions of the Minecraft protocol.
 * This class currently does not support decoding of the handshake packet.
 */
public class LegacyHandshakePacket implements MinecraftPacket {

  /**
   * Throws an {@link UnsupportedOperationException} because decoding is not supported.
   *
   * <p>This method is not intended to be called, as legacy handshake decoding
   * is not handled by this packet implementation.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   * @throws UnsupportedOperationException always
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    throw new UnsupportedOperationException();
  }

  /**
   * Throws an {@link UnsupportedOperationException} because encoding is not supported.
   *
   * <p>This method is not implemented since this packet is only used for inbound handling
   * in legacy versions and should not be serialized.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   * @throws UnsupportedOperationException always
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    throw new UnsupportedOperationException();
  }

  /**
   * Handles this legacy handshake packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates to {@code handler.handle(this)} to process the packet appropriately
   * during legacy client connections.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
