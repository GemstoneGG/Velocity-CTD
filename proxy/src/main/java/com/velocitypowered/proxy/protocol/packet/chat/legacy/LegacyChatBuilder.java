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

package com.velocitypowered.proxy.protocol.packet.chat.legacy;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.builder.ChatBuilderV2;
import java.util.UUID;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;

/**
 * A concrete implementation of {@link ChatBuilderV2} for handling legacy chat formats.
 *
 * <p>The {@code LegacyChatBuilder} is designed to support and build chat components
 * using legacy chat formatting, such as the formats used in earlier versions of Minecraft.
 * It extends the functionality of {@link ChatBuilderV2} to cater to older chat systems.</p>
 */
public class LegacyChatBuilder extends ChatBuilderV2 {

  /**
   * Constructs a new {@code LegacyChatBuilder} for the specified protocol version.
   *
   * @param version the protocol version targeted by this builder
   */
  public LegacyChatBuilder(final ProtocolVersion version) {
    super(version);
  }

  /**
   * Builds a {@link LegacyChatPacket} for sending a chat message to the client.
   *
   * <p>This method constructs a packet compatible with older Minecraft versions
   * by serializing the message as a legacy JSON string. If the sender identity is not set,
   * it falls back to {@link Identity#nil()}.</p>
   *
   * @return the constructed {@link LegacyChatPacket} to send to the client
   */
  @Override
  public MinecraftPacket toClient() {
    // This is temporary (this also has been here for a good while)
    UUID identity = sender == null ? (senderIdentity == null ? Identity.nil().uuid() : senderIdentity.uuid()) : sender.getUniqueId();
    Component msg = component == null ? Component.text(message) : component;

    return new LegacyChatPacket(ProtocolUtils.getJsonChatSerializer(version).serialize(msg), type.getId(), identity);
  }

  /**
   * Builds a {@link LegacyChatPacket} for sending a chat message to the server.
   *
   * <p>This method creates a new {@code LegacyChatPacket} and populates it with the plain
   * message content. No identity, chat type, or additional metadata is applied, as this format
   * targets legacy client-to-server communication.</p>
   *
   * @return the constructed {@link LegacyChatPacket} to send to the server
   */
  @Override
  public MinecraftPacket toServer() {
    LegacyChatPacket chat = new LegacyChatPacket();
    chat.setMessage(message);
    return chat;
  }
}
