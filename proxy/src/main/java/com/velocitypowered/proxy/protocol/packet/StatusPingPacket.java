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

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    randomId = buf.readLong();
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    buf.writeLong(randomId);
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public final int expectedMaxLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    return 8;
  }

  @Override
  public final int expectedMinLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    return 8;
  }
}
