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

package com.velocityctd.proxy.queue;

import static com.velocityctd.api.queue.ServerStatus.WAITING;

import com.velocityctd.api.queue.QueueState;
import com.velocityctd.proxy.queue.redis.depot.VelocityQueueDepotEntry;
import com.velocityctd.proxy.queue.redis.depot.VelocityQueueDepotService;
import com.velocityctd.proxy.queue.redis.packet.VelocityQueueSync;
import com.velocityctd.proxy.queue.util.QueueComponents;
import com.velocityctd.proxy.redis.data.VelocityActionBar;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Redis-aware extension of {@link VelocityQueueManager}.
 */
public final class RedisVelocityQueueManager extends VelocityQueueManager {

  public RedisVelocityQueueManager(@NotNull VelocityServer server) {
    super(server);
  }

  @Override
  protected void preInitialize() {
    loadFromRedis();
  }

  @Override
  protected void postInitialize() {
    server.getRedis().addReconnectListener(() ->
        server.getScheduler()
            .buildTask(VelocityVirtualPlugin.INSTANCE, this::reloadFromRedis)
            .schedule()
    );
  }

  @Override
  public boolean isMasterProxy() {
    List<String> masterProxies = server.getConfiguration().getQueue().getMasterProxyIds();
    List<String> activeProxies = new ArrayList<>(
        server.getRedis().getProxyService().getAllProxyIds());
    Collections.sort(activeProxies);

    String ownId = server.getProxyId();

    if (masterProxies.isEmpty() || (masterProxies.size() == 1 && masterProxies.getFirst().isEmpty())) {
      // No explicit master list: alphabetically-first active proxy is master
      return !activeProxies.isEmpty() && activeProxies.getFirst().equalsIgnoreCase(ownId);
    }

    // Find the first master-proxy ID that is currently active
    activeProxies.retainAll(masterProxies);
    if (activeProxies.isEmpty()) {
      return false;
    }

    for (String candidate : masterProxies) {
      if (activeProxies.contains(candidate)) {
        return candidate.equalsIgnoreCase(ownId);
      }
    }

    return false;
  }

  @Override
  protected boolean isPlayerOnline(UUID uuid) {
    return server.getRedis().getPlayerService().isPlayerOnline(uuid);
  }

  @Override
  protected RedisVelocityQueue createQueue(VelocityRegisteredServer rs, QueueState state) {
    return new RedisVelocityQueue(server, this, rs, state);
  }

  @Override
  protected void sendActionBar(VelocityQueueEntry entry) {
    Component component = QueueComponents.createActionbarComponent(entry);
    if (component != null) {
      server.getRedis().publish(new VelocityActionBar(entry.getUniqueId(), component));
    }
  }

  /**
   * Applies a {@link VelocityQueueSync} received from another proxy to the local
   * in-memory queue.
   *
   * @param sync the incoming sync data
   */
  public void handleSync(@NotNull VelocityQueueSync sync) {
    RedisVelocityQueue queue;
    try {
      queue = (RedisVelocityQueue) getQueue(sync.serverName());
    } catch (IllegalArgumentException ignored) {
      return; // unknown server
    }

    switch (sync.action()) {
      case ENQUEUE -> queue.applyEnqueue(sync);
      case DEQUEUE -> queue.applyDequeue(sync.playerUuid());
      case STATE_CHANGE -> queue.applyStateChange(sync.newState());
      case STATUS_CHANGE -> queue.applyStatusChange(sync.newStatus());
      case WAITING_CHANGE -> queue.applyWaitingChange(sync);
      default -> throw new IllegalStateException("Unknown action " + sync.action() + ".");
    }
  }

  /**
   * Loads persisted queue state from the Redis depot.
   * Called at startup ({@link #preInitialize()}) and on reconnect ({@link #reloadFromRedis()}).
   */
  private void loadFromRedis() {
    VelocityQueueDepotService service = server.getRedis().getQueueService();

    queues.clear();
    for (VelocityQueueDepotEntry entry : service.getAll()) {
      VelocityRegisteredServer rs = server.getServer(entry.getUniqueId()).orElse(null);
      if (rs != null) {
        queues.put(entry.getUniqueId(), new RedisVelocityQueue(server, this, rs, entry));

        // Seed the warmup timer so pingBackends() can promote a WAITING queue to ONLINE/FULL
        // once the delay elapses. Without this the queue would stay stuck in WAITING forever.
        if (entry.getServerStatus() == WAITING) {
          LAST_TURNED_ONLINE_TIME.put(entry.getUniqueId(), System.currentTimeMillis());
        }
      }
    }
  }

  /**
   * Reloads queue state from the Redis depot after a pub/sub reconnection.
   *
   * <p>Any packets that were missed during the disconnection window are recovered by
   * re-reading the master-written depot snapshot. If this proxy is currently master, it
   * also re-broadcasts all server statuses and queue states so non-master proxies recover.</p>
   */
  private void reloadFromRedis() {
    loadFromRedis();

    if (isMasterProxy()) {
      for (VelocityQueue queue : queues.values()) {
        server.getRedis().publish(VelocityQueueSync.statusChange(queue.getName(), queue.getServerStatus()));
        server.getRedis().publish(VelocityQueueSync.stateChange(queue.getName(), queue.getState()));
      }
    }
  }
}
