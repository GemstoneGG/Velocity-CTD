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
 * Represents a packet to handle the removal of a user from a queue of a server.
 * This is used for cross-proxy queue synchronization.
 *
 * @param playerUuid The UUID of the player that's being removed from the queue.
 * @param serverName The name of the server which the player is being removed from.
 * @param maxRetriesReached Whether the maximum number of retries has been reached.
 */
public record RedisQueueDequeueRequest(UUID playerUuid, String serverName, boolean maxRetriesReached) implements RedisPacket {

  /**
   * The identifier for this Redis packet type.
   */
  public static final String ID = "redis-queue-dequeue";

  @Override
  public String getId() {
    return ID;
  }
}
