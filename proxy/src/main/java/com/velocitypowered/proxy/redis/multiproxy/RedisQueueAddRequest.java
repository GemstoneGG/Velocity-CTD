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

package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.redis.RedisPacket;
import java.util.UUID;

/**
 * Represents a packet to handle adding a player to a queue across all proxies.
 * This ensures queue synchronization in multi-proxy setups.
 *
 * @param playerUuid The UUID of the player that's being added to the queue.
 * @param serverName The name of the server which the player is being queued for.
 * @param priority The priority level for the player in the queue.
 * @param fullBypass Whether the player should bypass full server checks.
 * @param queueBypass Whether the player should bypass the queue entirely.
 * @param username The username of the player for display purposes.
 */
public record RedisQueueAddRequest(UUID playerUuid, String serverName, int priority,
                                  boolean fullBypass, boolean queueBypass, String username) implements RedisPacket {

  /**
   * The identifier for this Redis packet type.
   */
  public static final String ID = "redis-queue-add";

  @Override
  public String getId() {
    return ID;
  }
}
