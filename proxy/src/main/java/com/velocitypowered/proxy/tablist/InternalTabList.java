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

package com.velocitypowered.proxy.tablist;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;

/**
 * Tab list interface with methods for handling player info packets.
 */
public interface InternalTabList extends TabList {

  /**
   * Returns the {@link Player} associated with this tab list.
   *
   * @return the player whose tab list is being modified
   */
  Player getPlayer();

  /**
   * Processes a legacy player list update packet.
   *
   * <p>This method is typically called when a server sends a {@link LegacyPlayerListItemPacket},
   * usually from versions before 1.8.</p>
   *
   * @param packet the legacy player list packet
   */
  default void processLegacy(LegacyPlayerListItemPacket packet) {
  }

  /**
   * Processes a modern upsert (add/update) player info packet and reflects it in the tab list.
   *
   * @param infoPacket the {@link UpsertPlayerInfoPacket} to apply
   */
  default void processUpdate(UpsertPlayerInfoPacket infoPacket) {
  }

  /**
   * Processes a removal packet for one or more players from the tab list.
   *
   * @param infoPacket the {@link RemovePlayerInfoPacket} to apply
   */
  default void processRemove(RemovePlayerInfoPacket infoPacket) {
  }

  /**
   * Clears all entries from the tab list without triggering any events or additional network updates.
   */
  void clearAllSilent();
}
