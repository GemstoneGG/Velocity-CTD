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

package com.velocitypowered.proxy.protocol.packet.chat;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * Represents a packet sent to acknowledge the receipt of a chat message.
 * This packet is used to confirm that a player or client has received and processed
 * a chat message from the server.
 */
public class ChatAcknowledgementPacket implements MinecraftPacket {

  /**
   * The offset of the last message acknowledged by the client.
   */
  int offset;

  /**
   * Constructs a {@code ChatAcknowledgementPacket} with a specific offset value.
   *
   * @param offset the acknowledgment offset
   */
  public ChatAcknowledgementPacket(final int offset) {
    this.offset = offset;
  }

  /**
   * Constructs an empty {@code ChatAcknowledgementPacket} for decoding.
   */
  public ChatAcknowledgementPacket() {
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    offset = ProtocolUtils.readVarInt(buf);
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, offset);
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public final String toString() {
    return "ChatAcknowledgement{"
        + "offset=" + offset
        + '}';
  }

  /**
   * Returns the offset value carried in this packet.
   *
   * @return the message acknowledgment offset
   */
  public int offset() {
    return offset;
  }
}
