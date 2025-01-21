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

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.cache.StandardRetriever;

/**
 * Manages the queue system without Redis.
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
   * Updates the actionbar message for all players.
   */
  public void tickMessageForAllPlayers() {
    for (ServerQueueStatus status : this.cache.getAll()) {
      status.getActivePlayers().forEach((entry, playerUuid) ->
          server.getPlayer(playerUuid).ifPresent(player ->
              status.getEntry(player.getUniqueId()).ifPresent(queueEntry ->
                  player.sendActionBar(status.getActionBarComponent(queueEntry))
              )
          )
      );
    }
  }
}
