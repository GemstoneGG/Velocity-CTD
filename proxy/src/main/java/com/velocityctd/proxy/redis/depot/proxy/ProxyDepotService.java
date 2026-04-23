/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

package com.velocityctd.proxy.redis.depot.proxy;

import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.depot.AbstractDepotService;
import com.velocityctd.proxy.redis.depot.player.PlayerEntry;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.time.Duration;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an extension of the {@link AbstractDepotService} for the proxy depot, including
 * functionality to track certain information about a single proxy, or multiple proxies.
 */
public final class ProxyDepotService extends AbstractDepotService<String, ProxyEntry> {

  private static final Logger LOGGER = LogManager.getLogger(ProxyDepotService.class);

  /**
   * How often each proxy publishes its heartbeat to Redis.
   */
  public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(1);

  /**
   * How long a heartbeat key lives in Redis before it expires.
   * A proxy that stops publishing (e.g. killed with {@code kill -9}) will be reaped by
   * any surviving proxy once this TTL elapses without a renewal.
   */
  public static final Duration HEARTBEAT_TTL = Duration.ofSeconds(5);

  /**
   * Redis key prefix for per-proxy heartbeat keys. The full key is {@code heartbeat:<proxyId>}.
   */
  private static final String HEARTBEAT_KEY_PREFIX = "heartbeat:";

  /**
   * The Redis manager used to interact with proxy-related data stored in Redis.
   */
  private final VelocityRedis redis;

  /**
   * Scheduled task that publishes this proxy's heartbeat key to Redis every {@link #HEARTBEAT_INTERVAL}.
   */
  private final ScheduledTask heartbeatTask;

  /**
   * Scheduled task that checks for proxies whose heartbeat has expired and reaps their stale data.
   */
  private final ScheduledTask reapDeadProxiesTask;

  /**
   * Constructs a new {@link ProxyDepotService}.
   *
   * @param redis the {@link VelocityRedis} instance
   */
  public ProxyDepotService(@NotNull VelocityRedis redis) {
    super(ProxyEntry.class, redis.getProvider());

    this.redis = redis;

    this.depot.upsert(new ProxyEntry(redis.getServer()));

    this.heartbeatTask = redis.getServer().getScheduler()
            .buildTask(VelocityVirtualPlugin.INSTANCE, this::publishHeartbeat)
            .repeat(HEARTBEAT_INTERVAL)
            .schedule();

    this.reapDeadProxiesTask = redis.getServer().getScheduler()
            .buildTask(VelocityVirtualPlugin.INSTANCE, this::reapDeadProxies)
            .repeat(HEARTBEAT_INTERVAL)
            .schedule();
  }

  @Override
  public void teardown() {
    if (this.heartbeatTask != null) {
      this.heartbeatTask.cancel();
    }

    if (this.reapDeadProxiesTask != null) {
      this.reapDeadProxiesTask.cancel();
    }

    // Delete own heartbeat key so surviving proxies don't try to reap us while we're cleaning up.
    this.redis.getProvider().deleteKey(HEARTBEAT_KEY_PREFIX + this.redis.getProxyId());

    ProxyEntry proxyEntry = this.get(this.redis.getServer().getProxyId());
    if (proxyEntry != null) {
      proxyEntry.remove();
    }
  }

  /**
   * Get a list of all the {@link ProxyEntry proxy} IDs currently present in the depot.
   *
   * @return the list of all proxy IDs, sorted alphabetically
   */
  public List<String> getAllProxyIds() {
    return this.depot.keys().stream().sorted().toList();
  }

  /**
   * Get a list of all the {@link ProxyEntry proxy} IDs currently present in the depot, in lower case.
   *
   * @return the list of all proxy IDs in lower case, sorted alphabetically
   */
  public List<String> getAllProxyIdsLowerCase() {
    return this.depot.keys().stream().map(String::toLowerCase).sorted().toList();
  }

  /**
   * Publishes this proxy's heartbeat key to Redis with a TTL of {@link #HEARTBEAT_TTL}.
   * Called every {@link #HEARTBEAT_INTERVAL} by the scheduler.
   */
  private void publishHeartbeat() {
    if (this.redis.isShutdown()) {
      return;
    }

    this.redis.getProvider().setWithExpiry(
            HEARTBEAT_KEY_PREFIX + this.redis.getProxyId(),
            "1",
            HEARTBEAT_TTL.toSeconds()
    );
  }

  /**
   * Checks all known proxies in Redis for a live heartbeat key. Any proxy whose heartbeat
   * has expired is considered dead and its player and proxy entries are removed from Redis.
   * Called every {@link #HEARTBEAT_INTERVAL} by the scheduler.
   */
  private void reapDeadProxies() {
    if (this.redis.isShutdown()) {
      return;
    }

    for (String proxyId : this.getAllProxyIds()) {
      if (proxyId.equalsIgnoreCase(this.redis.getProxyId())) {
        continue; // Never reap ourselves.
      }

      if (this.redis.getProvider().existsKey(HEARTBEAT_KEY_PREFIX + proxyId)) {
        continue; // Proxy is alive.
      }

      reapProxy(proxyId);
    }
  }

  /**
   * Removes all Redis entries belonging to the given dead proxy: first its player entries,
   * then the proxy entry itself.
   *
   * @param proxyId the ID of the proxy to reap
   */
  private void reapProxy(@NotNull String proxyId) {
    LOGGER.warn("Reaping proxy {} from redis. This proxy shut down improperly.", proxyId);

    for (PlayerEntry playerEntry : this.redis.getPlayerService().getPlayerEntriesOnProxy(proxyId)) {
      playerEntry.remove();
    }

    this.depot.remove(proxyId);
  }
}
