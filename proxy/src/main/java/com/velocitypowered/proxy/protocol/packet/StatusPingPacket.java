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
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;

/**
 * Represents a status ping packet sent by the client to the server, which is used to measure the latency
 * between the client and server.
 */
public class StatusPingPacket implements MinecraftPacket {

  /**
   * The random identifier used to correlate the status ping request and response.
   *
   * <p>This value is sent by the client and echoed back by the server in the pong
   * to measure round-trip latency.</p>
   */
  private long randomId;

  /**
   * Decodes this status ping packet from the given {@link ByteBuf}.
   *
   * <p>This reads the random identifier sent by the client to later be echoed in the pong
   * response to measure latency.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    randomId = buf.readLong();
  }

  /**
   * Encodes this status ping packet into the given {@link ByteBuf}.
   *
   * <p>This writes the random identifier that will be echoed back by the server.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    buf.writeLong(randomId);
  }

  /**
   * Handles this status ping packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates handling to {@code handler.handle(this)} to initiate a pong reply.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Computes the expected maximum length (in bytes) of this status ping packet.
   *
   * <p>Because the packet only contains a single 64-bit payload field (a {@code long}),
   * the total size is fixed at {@code 8} bytes for all protocol versions and directions.</p>
   *
   * @param buf the buffer being read (unused)
   * @param direction the packet direction (clientbound or serverbound)
   * @param version the Minecraft protocol version
   * @return the fixed maximum byte length of this packet ({@code 8})
   */
  @Override
  public int decodeExpectedMaxLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    return 8;
  }

  /**
   * Computes the expected minimum length (in bytes) of this status ping packet.
   *
   * <p>As the payload is a single {@code long} value, the encoded size is always exactly
   * {@code 8} bytes. This method mirrors {@link #decodeExpectedMaxLength(ByteBuf, Direction, ProtocolVersion)}
   * for consistency and validation purposes.</p>
   *
   * @param buf the buffer being read (unused)
   * @param direction the packet direction (clientbound or serverbound)
   * @param version the Minecraft protocol version
   * @return the fixed minimum byte length of this packet ({@code 8})
   */
  @Override
  public int decodeExpectedMinLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    return 8;
  }
}
