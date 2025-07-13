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

package com.velocitypowered.proxy.protocol;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import io.netty.buffer.ByteBuf;

/**
 * Represents a Minecraft packet.
 */
public interface MinecraftPacket {

  /**
   * Decodes the contents of the packet from the provided buffer.
   *
   * @param buf the {@link ByteBuf} to read from
   * @param direction the packet direction (client to server or server to client)
   * @param protocolVersion the current Minecraft protocol version
   */
  void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion);

  /**
   * Encodes the contents of the packet into the provided buffer.
   *
   * @param buf the {@link ByteBuf} to write to
   * @param direction the packet direction (client to server or server to client)
   * @param protocolVersion the current Minecraft protocol version
   */
  void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion);

  /**
   * Handles the packet using the provided session handler.
   *
   * @param handler the {@link MinecraftSessionHandler} to process this packet
   * @return {@code true} if the packet was successfully handled, {@code false} otherwise
   */
  boolean handle(MinecraftSessionHandler handler);

  /**
   * Provides the expected maximum number of bytes required to decode this packet.
   * This value is used to detect malformed or malicious packets during decoding.
   *
   * @param buf the {@link ByteBuf} for reading the packet
   * @param direction the packet direction
   * @param version the Minecraft protocol version
   * @return the maximum number of expected bytes, or -1 if unknown
   */
  default int expectedMaxLength(ByteBuf buf, ProtocolUtils.Direction direction,
                                ProtocolVersion version) {
    return -1;
  }

  /**
   * Provides the expected minimum number of bytes required to decode this packet.
   * This value is used to validate that the packet meets the minimal structural requirements.
   *
   * @param buf the {@link ByteBuf} for reading the packet
   * @param direction the packet direction
   * @param version the Minecraft protocol version
   * @return the minimum number of expected bytes
   */
  default int expectedMinLength(ByteBuf buf, ProtocolUtils.Direction direction,
                                ProtocolVersion version) {
    return 0;
  }
}
