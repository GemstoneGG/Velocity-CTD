/*
 * Copyright (C) 2020-2024 Velocity Contributors
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

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.cache.StandardRetriever;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the queue system without redis.
 */
public class QueueManagerNoRedisImpl extends QueueManager {

  /**
   * Constructs a {@link QueueManagerRedisImpl}.
   *
   * @param server the proxy server
   */
  public QueueManagerNoRedisImpl(final VelocityServer server) {
    super(server);
    this.cache = new StandardRetriever(server);
  }

  /**
   * Checks whether the current proxy is the current master-proxy or not.
   *
   * @return whether the current proxy is the current master-proxy or not.
   */
  public boolean isMasterProxy() {
    return true;
  }

  /**
   * Updates the actionbar message for this player.
   */
  public void tickMessageForAllPlayers() {
    Map<Player, ServerQueueStatus> temp = new HashMap<>();
    String filter = this.config.getMultipleServerMessagingSelection();

    for (ServerQueueStatus status : this.cache.getAll()) {
      Map<ServerQueueEntry, UUID> map = status.getActivePlayers();
      for (ServerQueueEntry entry : map.keySet()) {
        UUID player = map.get(entry);

        Player p = server.getPlayer(player).orElse(null);
        if (p == null) {
          return;
        }

        if (filter.equalsIgnoreCase("first") && temp.containsKey(p)) {
          continue;
        }

        temp.put(p, status);

      }
    }

    for (Player player : temp.keySet()) {
      ServerQueueStatus status = temp.get(player);
      status.getEntry(player.getUniqueId()).ifPresent(entry -> player.sendActionBar(temp.get(player).getActionBarComponent(entry)));
    }
  }
}
