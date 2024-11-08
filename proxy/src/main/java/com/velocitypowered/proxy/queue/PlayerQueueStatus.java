/*
 * Copyright (C) 2024 Velocity Contributors
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

package com.velocitypowered.proxy.queue;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.ArrayDeque;
import java.util.Deque;
import net.kyori.adventure.text.Component;

/**
 * The status of the queue for a single player.
 */
public class PlayerQueueStatus {
  private final ConnectedPlayer player;
  private final VelocityServer server;
  final Deque<ServerQueueEntry> queueEntries = new ArrayDeque<>();

  public PlayerQueueStatus(final ConnectedPlayer player, final VelocityServer server) {
    this.player = player;
    this.server = server;
  }

  /**
   * Updates the actionbar message for this player.
   */
  public void tickMessage() {
    if (queueEntries.isEmpty()) {
      return;
    }

    ServerQueueEntry entry = switch (this.server.getConfiguration().getQueue().getMultipleServerMessagingSelection()) {
      case "first" -> queueEntries.getFirst();
      default -> queueEntries.getLast();
    };

    ServerQueueStatus queue = entry.target.getQueueStatus();
    Component actionBar = queue.getActionBarComponent(entry);
    this.player.sendActionBar(actionBar);
  }
}
