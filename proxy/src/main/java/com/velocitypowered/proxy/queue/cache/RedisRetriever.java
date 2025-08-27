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

package com.velocitypowered.proxy.queue.cache;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.redis.RedisManagerImpl;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The redis implementation of the queue cache.
 */
public class RedisRetriever implements QueueCacheRetriever {

  private static final Logger logger = LoggerFactory.getLogger(RedisRetriever.class);

  /**
   * The Velocity proxy instance.
   */
  private final VelocityServer proxy;

  /**
   * The Redis manager implementation for interacting with the Redis backend.
   */
  private final RedisManagerImpl redisManager;

  /**
   * Instance cache to ensure the same ServerQueueStatus instance is returned for the same server.
   * This prevents the issue where each getQueueStatus() call creates a new instance.
   */
  private final ConcurrentHashMap<String, ServerQueueStatus> instanceCache = new ConcurrentHashMap<>();

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

    // Check if we have a cached instance first
    ServerQueueStatus cachedInstance = instanceCache.get(serverName);
    if (cachedInstance != null) {
      logger.debug("Returning cached ServerQueueStatus instance for server: {}", serverName);
      return cachedInstance;
    }

    // If no cached instance, try to get from Redis
    SerializableQueue ser = this.redisManager.getQueue(serverName);
    ServerQueueStatus status = null;
    if (ser != null) {
      status = ser.convert(this.proxy, server);
      logger.debug("Created new ServerQueueStatus instance from Redis for server: {}", serverName);
    }

    if (status == null) {
      status = new ServerQueueStatus(server, proxy);
      logger.debug("Created new empty ServerQueueStatus instance for server: {}", serverName);

      // Make the queue if it doesn't exist.
      redisManager.addOrUpdateQueue(status);
    }

    // Cache the instance for future use
    ServerQueueStatus existingInstance = instanceCache.putIfAbsent(serverName, status);
    if (existingInstance != null) {
      // Another thread created an instance while we were processing, use that one
      logger.debug("Another thread created ServerQueueStatus instance for server: {}, using existing", serverName);
      return existingInstance;
    }

    logger.debug("Cached new ServerQueueStatus instance for server: {}", serverName);
    return status;
  }

  @Override
  public final ServerQueueStatus get(final UUID uuid) {
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
        // Use the cached instance if available, otherwise create new one
        ServerQueueStatus status = instanceCache.get(s.getServerName());
        if (status == null) {
          status = s.convert(proxy, server);
          instanceCache.putIfAbsent(s.getServerName(), status);
        }
        queue.add(status);
      }
    });

    return queue;
  }

  /**
   * Clears the instance cache. This should be called when the queue system is being reset.
   */
  public void clearCache() {
    logger.debug("Clearing RedisRetriever instance cache");
    instanceCache.clear();
  }
}
