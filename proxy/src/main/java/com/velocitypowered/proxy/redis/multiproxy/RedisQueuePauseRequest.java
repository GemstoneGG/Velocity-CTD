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

package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.redis.RedisPacket;

/**
 * Constructs a request to pause a queue.
 *
 * @param server The name of the server.
 * @param pause Whether to pause it or not.
 */
public record RedisQueuePauseRequest(String server, boolean pause) implements RedisPacket {
  public static final String ID = "redis-queue-pause";

  @Override
  public String getId() {
    return ID;
  }
}
