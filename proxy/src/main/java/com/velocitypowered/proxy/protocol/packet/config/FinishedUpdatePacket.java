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

package com.velocitypowered.proxy.protocol.packet.config;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * The {@code FinishedUpdatePacket} class represents a packet that signals the completion
 * of an update process between the client and server in the Minecraft protocol.
 *
 * <p>This packet is used to indicate that the client has finished receiving and processing
 * an update, ensuring that further operations can proceed.</p>
 */
public final class FinishedUpdatePacket implements MinecraftPacket {

  /**
   * A shared singleton instance of {@code FinishedUpdatePacket}, since the packet carries no data.
   */
  public static final FinishedUpdatePacket INSTANCE = new FinishedUpdatePacket();

  private FinishedUpdatePacket() {
  }

  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
  }

  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
  }

  @Override
  public int expectedMaxLength(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    return 0;
  }

  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
