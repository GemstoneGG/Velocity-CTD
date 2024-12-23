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

package com.velocitypowered.proxy.queue.cache;

import com.velocitypowered.proxy.queue.ServerQueueStatus;
import java.util.List;

/**
 * Represents the cache of all the queues.
 */
public interface QueueCacheRetriever {

  /**
   * Gets a queue.
   *
   * @param serverName The name of the server.
   * @return The queue.
   */
  ServerQueueStatus get(String serverName);

  /**
   * Gets all the queues.
   *
   * @return All the queues.
   */
  List<ServerQueueStatus> getAll();
}
