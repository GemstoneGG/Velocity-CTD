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

package com.velocitypowered.proxy.protocol;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import io.netty.buffer.ByteBuf;

/**
 * Represents a Minecraft packet.
 */
public interface MinecraftPacket {

  /**
   * Decodes the contents of this packet from the specified {@link ByteBuf}.
   *
   * @param buf the buffer containing the packet data
   * @param direction the packet direction (client to server or server to client)
   * @param protocolVersion the current Minecraft protocol version
   */
  void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion);

  /**
   * Encodes the contents of this packet into the specified {@link ByteBuf}.
   *
   * @param buf the buffer to write the packet data into
   * @param direction the packet direction (client to server or server to client)
   * @param protocolVersion the current Minecraft protocol version
   */
  void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion);

  /**
   * Handles this packet using the provided {@link MinecraftSessionHandler}.
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully,
   *         or {@code false} if it was unrecognized or unprocessed
   */
  boolean handle(MinecraftSessionHandler handler);

  /**
   * Returns the maximum number of bytes expected to be read when decoding this packet.
   * This is primarily used to guard against malformed or malicious packets that exceed
   * reasonable size expectations.
   *
   * <p>Implementations should override this if a reliable upper bound is known.
   *
   * @param buf the buffer being read from
   * @param direction the packet direction
   * @param version the Minecraft protocol version
   * @return the maximum expected byte length, or {@code -1} if unknown
   */
  default int decodeExpectedMaxLength(ByteBuf buf, ProtocolUtils.Direction direction,
                                      ProtocolVersion version) {
    return -1;
  }

  /**
   * Returns the minimum number of bytes required to decode this packet.
   * This ensures that the packet contains at least enough data to represent
   * its required structure.
   *
   * @param buf the buffer being read from
   * @param direction the packet direction
   * @param version the Minecraft protocol version
   * @return the minimum expected byte length
   */
  default int decodeExpectedMinLength(ByteBuf buf, ProtocolUtils.Direction direction,
                                      ProtocolVersion version) {
    return 0;
  }

  /**
   * Provides an estimated number of bytes required to encode this packet.
   * This value serves as a preallocation hint for internal buffer operations
   * during packet encoding.
   *
   * <p>Implementations may calculate this by summing the expected sizes of
   * encoded elements, such as string lengths or VarInt counts. For example:
   *
   * @param direction the packet direction (client to server or server to client)
   * @param version the Minecraft protocol version
   * @return the estimated encoded size in bytes, or {@code -1} if unknown
   */
  default int encodeSizeHint(ProtocolUtils.Direction direction, ProtocolVersion version) {
    return -1;
  }
}
