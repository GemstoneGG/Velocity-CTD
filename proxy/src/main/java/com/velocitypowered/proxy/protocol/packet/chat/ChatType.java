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

/**
 * Represents different types of chat messages in the game, defining how a message should be
 * handled and displayed.
 * This enum categorizes various chat message types such as system messages, player chat,
 * or game info.
 */
public enum ChatType {

  /**
   * Standard player chat messages that appear in the chat box.
   * These messages are typically sent by players and displayed with their name prefix.
   */
  CHAT((byte) 0),

  /**
   * System messages that are displayed in the chat box but are not attributed to a player.
   * These are often used for administrative messages or notifications.
   */
  SYSTEM((byte) 1),

  /**
   * Game information messages that appear above the hotbar (action bar).
   * These messages are often transient and used for status updates or prompts.
   */
  GAME_INFO((byte) 2);

  /**
   * The raw byte value of the chat type, used in legacy protocol serialization.
   */
  private final byte raw;

  ChatType(final byte raw) {
    this.raw = raw;
  }

  /**
   * Returns the protocol-specific byte ID for this chat type.
   *
   * @return the raw byte value of the chat type
   */
  public byte getId() {
    return raw;
  }
}
