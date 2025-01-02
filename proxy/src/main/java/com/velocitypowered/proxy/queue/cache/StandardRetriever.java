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

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The non-redis implementation of the redis queue.
 */
public class StandardRetriever implements QueueCacheRetriever {
  private final Map<String, ServerQueueStatus> queues = new HashMap<>();

  private final VelocityServer proxy;

  /**
   * Constructs a new non redis queue cache.
   *
   * @param proxy The proxy.
   */
  public StandardRetriever(final VelocityServer proxy) {
    this.proxy = proxy;
  }

  /**
   * Gets the queue.
   *
   * @param serverName The name of the server.
   * @return The queue.
   */
  @Override
  public ServerQueueStatus get(final String serverName) {
    VelocityRegisteredServer server = (VelocityRegisteredServer) proxy.getServer(serverName).orElse(null);
    if (server == null) {
      throw new IllegalArgumentException("Attempted to fetch queue for invalid server: '" + serverName + "'");
    }

    return queues.computeIfAbsent(serverName, k -> new ServerQueueStatus(server, proxy));
  }

  @Override
  public ServerQueueStatus get(final UUID uuid) {
    for (ServerQueueStatus status : getAll()) {
      if (status.isQueued(uuid)) {
        return status;
      }
    }
    return null;
  }

  /**
   * Gets all the queues.
   *
   * @return All the queues.
   */
  @Override
  public List<ServerQueueStatus> getAll() {
    return queues.values().stream().toList();
  }
}
