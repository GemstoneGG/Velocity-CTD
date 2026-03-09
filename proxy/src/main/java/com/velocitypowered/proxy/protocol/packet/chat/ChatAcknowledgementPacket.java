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

  /**
   * Decodes this chat acknowledgement packet from the given {@link ByteBuf}.
   *
   * <p>This reads the offset of the last message acknowledged by the client.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    offset = ProtocolUtils.readVarInt(buf);
  }

  /**
   * Encodes this chat acknowledgement packet into the given {@link ByteBuf}.
   *
   * <p>This writes the offset indicating the last chat message acknowledged by the client.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, offset);
  }

  /**
   * Handles this chat acknowledgement packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to track client-side
   * message acknowledgment state.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Returns a string representation of this chat acknowledgement packet.
   *
   * <p>This includes the acknowledged message offset value.</p>
   *
   * @return a string describing the packet
   */
  @Override
  public String toString() {
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
