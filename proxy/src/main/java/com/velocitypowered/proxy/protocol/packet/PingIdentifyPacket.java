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
 * Represents a packet used for ping identification with a unique ID.
 */
public class PingIdentifyPacket implements MinecraftPacket {

  /**
   * The ID used to identify the ping request or response.
   * This value is typically echoed back to match requests with responses.
   */
  private int id;

  /**
   * Returns a string representation of this ping identify packet.
   *
   * <p>This includes the ping identifier value.</p>
   *
   * @return a string describing the packet
   */
  @Override
  public String toString() {
    return "Ping{"
        + "proxyId="
        + id
        + '}';
  }

  /**
   * Decodes this ping identify packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the integer identifier used to correlate the ping response.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    id = buf.readInt();
  }

  /**
   * Encodes this ping identify packet into the given {@link ByteBuf}.
   *
   * <p>This writes the integer identifier so it can be echoed by the recipient.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    buf.writeInt(id);
  }

  /**
   * Handles this ping identify packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to match or respond
   * to the ping identifier.</p>
   *
   * @param handler the session handler responsible for handling this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
