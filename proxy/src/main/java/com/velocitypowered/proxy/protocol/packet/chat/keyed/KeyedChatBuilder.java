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

package com.velocitypowered.proxy.protocol.packet.chat.keyed;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ChatType;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.builder.ChatBuilderV2;
import net.kyori.adventure.text.Component;

/**
 * A concrete implementation of {@link ChatBuilderV2} that uses keys to build chat components.
 *
 * <p>The {@code KeyedChatBuilder} class extends the functionality of {@link ChatBuilderV2} by allowing
 * chat components to be built using specific keys, enabling dynamic message construction.</p>
 */
public class KeyedChatBuilder extends ChatBuilderV2 {

  /**
   * Constructs a new {@code KeyedChatBuilder} for the given protocol version.
   *
   * @param version the protocol version this builder targets
   */
  public KeyedChatBuilder(final ProtocolVersion version) {
    super(version);
  }

  /**
   * Builds a {@link SystemChatPacket} to be sent to the client.
   *
   * <p>If the {@link #component} is set, it will be used as the chat message;
   * otherwise, a plain text {@link Component} is created from the {@link #message} string.</p>
   *
   * <p>The packet will use {@link ChatType#SYSTEM} unless the original type is not {@link ChatType#CHAT}.</p>
   *
   * @return the constructed {@link SystemChatPacket} to be sent to the client
   */
  @Override
  public MinecraftPacket toClient() {
    // This is temporary (but doesn't seem so temporary)
    Component msg = component == null ? Component.text(message) : component;
    return new SystemChatPacket(new ComponentHolder(version, msg), type == ChatType.CHAT ? ChatType.SYSTEM : type);
  }

  /**
   * Builds a {@link MinecraftPacket} to be sent to the server.
   *
   * <p>If the message begins with {@code /}, a {@link KeyedPlayerCommandPacket} is created
   * with the leading slash removed. Otherwise, a {@link KeyedPlayerChatPacket} is constructed,
   * with the timestamp applied as an expiry value.</p>
   *
   * <p>Note: Sending {@link KeyedPlayerChatPacket} directly may trigger an error on modern servers,
   * but is included for legacy support or fallback behavior.</p>
   *
   * @return the constructed {@link MinecraftPacket} to be sent to the server
   */
  @Override
  public MinecraftPacket toServer() {
    if (message.startsWith("/")) {
      return new KeyedPlayerCommandPacket(message.substring(1), ImmutableList.of(), timestamp);
    } else {
      // This will produce an error on the server, but needs to be here.
      KeyedPlayerChatPacket v1Chat = new KeyedPlayerChatPacket(message);
      v1Chat.setExpiry(this.timestamp);
      return v1Chat;
    }
  }
}
