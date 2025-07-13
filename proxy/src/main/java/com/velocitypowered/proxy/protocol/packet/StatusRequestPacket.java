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
 * Represents a status request packet sent by the client to the server to request the server's status.
 */
public final class StatusRequestPacket implements MinecraftPacket {

  /**
   * Singleton instance of the {@link StatusRequestPacket}, as this packet contains no payload.
   *
   * <p>This instance can be reused wherever a {@code StatusRequestPacket} is needed
   * since it holds no state.</p>
   */
  public static final StatusRequestPacket INSTANCE = new StatusRequestPacket();

  private StatusRequestPacket() {
  }

  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    // There is no additional data to decode.
  }

  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    // There is no data to decode.
  }

  @Override
  public String toString() {
    return "StatusRequest";
  }

  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public int expectedMaxLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    return 0;
  }
}
