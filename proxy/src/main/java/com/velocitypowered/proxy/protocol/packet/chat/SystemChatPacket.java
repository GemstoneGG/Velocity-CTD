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
 * Represents a packet sent from the server to the client to display system chat messages.
 * This packet handles the communication of messages that are not player-generated, but instead
 * come from the system or server itself.
 */
public class SystemChatPacket implements MinecraftPacket {

  /**
   * The chat component to display, stored in JSON or NBT format.
   */
  private ComponentHolder component;

  /**
   * The type of chat (e.g., system message, action bar).
   */
  private ChatType type;

  /**
   * Constructs an empty {@code SystemChatPacket} for decoding.
   */
  public SystemChatPacket() {
  }

  /**
   * Constructs a {@code SystemChatPacket} with the given component and chat type.
   *
   * @param component the chat message to send
   * @param type the display context for the chat message
   */
  public SystemChatPacket(final ComponentHolder component, final ChatType type) {
    this.component = component;
    this.type = type;
  }

  /**
   * Returns the {@link ChatType} for this system message.
   *
   * @return the chat type (e.g., SYSTEM, GAME_INFO)
   */
  public ChatType getType() {
    return type;
  }

  /**
   * Returns the {@link ComponentHolder} containing the message.
   *
   * @return the text component for this message
   */
  public ComponentHolder getComponent() {
    return component;
  }

  /**
   * Decodes this system chat packet from the given {@link ByteBuf}.
   *
   * <p>This reads the chat component and type of message, which may be represented
   * as a boolean or VarInt depending on the protocol version.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    component = ComponentHolder.read(buf, version);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
      type = buf.readBoolean() ? ChatType.GAME_INFO : ChatType.SYSTEM;
    } else {
      type = ChatType.values()[ProtocolUtils.readVarInt(buf)];
    }
  }

  /**
   * Encodes this system chat packet into the given {@link ByteBuf}.
   *
   * <p>This writes the chat component and message type using version-specific
   * serialization rules (boolean or VarInt).</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   * @throws IllegalArgumentException if the chat type is not recognized
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    component.write(buf);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
      switch (type) {
        case SYSTEM -> buf.writeBoolean(false);
        case GAME_INFO -> buf.writeBoolean(true);
        default -> throw new IllegalArgumentException("Invalid chat type");
      }
    } else {
      ProtocolUtils.writeVarInt(buf, type.getId());
    }
  }

  /**
   * Handles this system chat packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates packet processing to {@code handler.handle(this)}.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
