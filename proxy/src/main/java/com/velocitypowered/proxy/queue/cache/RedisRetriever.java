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
import com.velocitypowered.proxy.redis.RedisManagerImpl;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The redis implementation of the queue cache.
 */
public class RedisRetriever implements QueueCacheRetriever {

  private final VelocityServer proxy;
  private final RedisManagerImpl redisManager;

  /**
   * Constructs a new redis retriever.
   *
   * @param proxy The proxy.
   */
  public RedisRetriever(final VelocityServer proxy) {
    this.proxy = proxy;
    this.redisManager = proxy.getRedisManager();
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
      return null;
    }
    SerializableQueue ser = this.redisManager.getQueue(serverName);

    ServerQueueStatus status = null;
    if (ser != null) {
      status = ser.convert(this.proxy, server);
    }
    if (status == null) {
      status = new ServerQueueStatus(server, proxy);

      // Make the queue if it doesn't exist.
      redisManager.addOrUpdateQueue(status);
    }

    return status;
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
    List<SerializableQueue> ser = redisManager.getAllQueues();
    List<ServerQueueStatus> queue = new ArrayList<>();
    ser.forEach(s -> {
      VelocityRegisteredServer server = (VelocityRegisteredServer) proxy.getServer(s.getServerName()).orElse(null);

      if (server != null) {
        queue.add(s.convert(proxy, server));
      }
    });

    return queue;
  }
}
