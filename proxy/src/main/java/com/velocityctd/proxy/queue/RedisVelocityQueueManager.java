/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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
import static java.util.Objects.requireNonNull;

import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.proxy.queue.redis.depot.VelocityQueueDepotEntry;
import com.velocityctd.proxy.queue.redis.depot.VelocityQueueDepotService;
import com.velocityctd.proxy.queue.redis.packet.VelocityQueueSync;
import com.velocityctd.proxy.queue.util.QueueComponents;
import com.velocityctd.proxy.redis.data.VelocityActionBar;
import com.velocityctd.proxy.redis.depot.proxy.ProxyDepotService;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Redis-aware extension of {@link VelocityQueueManager}.
 */
public final class RedisVelocityQueueManager extends VelocityQueueManager {

  private static final Logger LOGGER = LogManager.getLogger(RedisVelocityQueueManager.class);

  private volatile boolean cachedIsMaster;
  private @Nullable ScheduledTask masterRefreshTask;

  public RedisVelocityQueueManager(@NotNull VelocityServer server) {
    super(server);
  }

  @Override
  protected void preInitialize() {
    super.preInitialize();

    loadFromRedis();
  }

  @Override
  protected void postInitialize() {
    super.postInitialize();

    // Compute once synchronously so isMasterProxy() returns the correct value before
    // any of the scheduled queue tasks (transfer/ping/actionbar) get to run.
    refreshMasterStatus();

    masterRefreshTask = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::refreshMasterStatus)
        .repeat(ProxyDepotService.HEARTBEAT_INTERVAL)
        .schedule();

    scheduleOfflineRemovals();

    server.getRedis().addReconnectListener(() ->
        server.getScheduler()
            .buildTask(VelocityVirtualPlugin.INSTANCE, this::reloadFromRedis)
            .schedule()
    );
  }

  @Override
  public void teardown() {
    if (masterRefreshTask != null) {
      masterRefreshTask.cancel();
      masterRefreshTask = null;
    }
    super.teardown();
  }

  @Override
  public boolean isMasterProxy() {
    return cachedIsMaster;
  }

  /**
   * Recomputes whether this proxy is the master and updates {@link #cachedIsMaster}.
   */
  private void refreshMasterStatus() {
    if (server.getRedis().isShutdown()) {
      return;
    }

    boolean newIsMaster = computeIsMaster();
    boolean previous = cachedIsMaster;
    cachedIsMaster = newIsMaster;

    if (previous != newIsMaster) {
      if (newIsMaster) {
        LOGGER.info("This proxy has become the master queue proxy.");
      } else {
        LOGGER.info("This proxy is no longer the master queue proxy.");
      }
    }
  }

  private boolean computeIsMaster() {
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
  protected void onQueueRemoved(@NotNull String serverName) {
    if (isMasterProxy() && !server.getRedis().isShutdown()) {
      server.getRedis().getQueueService().removeQueue(serverName);
    }
  }

  @Override
  public @NotNull RedisVelocityQueue getQueue(@NotNull String serverName) {
    return (RedisVelocityQueue) super.getQueue(serverName);
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
      queue = getQueue(sync.serverName());
    } catch (IllegalArgumentException ignored) {
      return; // unknown server
    }

    switch (sync.action()) {
      case ENQUEUE -> queue.applyEnqueue(new QueueEntryData(
          requireNonNull(sync.playerUuid(), "playerUuid"),
          requireNonNull(sync.username(), "username"),
          sync.priority(),
          sync.fullBypass(),
          sync.queueBypass()));
      case DEQUEUE -> queue.applyDequeue(
          requireNonNull(sync.playerUuid(), "playerUuid"));
      case STATE_CHANGE -> queue.applyStateChange(
          requireNonNull(sync.newState(), "newState"));
      case STATUS_CHANGE -> queue.applyStatusChange(
          requireNonNull(sync.newStatus(), "newStatus"));
      case WAITING_CHANGE -> queue.applyWaitingChange(
          requireNonNull(sync.playerUuid(), "playerUuid"),
          sync.waitingForConnection(),
          sync.connectionAttempts(),
          sync.updatedPriority(),
          sync.updatedFullBypass(),
          sync.updatedQueueBypass());
      case OFFLINE_CHANGE -> queue.applyOfflineChange(
          requireNonNull(sync.playerUuid(), "playerUuid"),
          sync.offlineSinceMs(),
          sync.offlineTimeoutSeconds());
      default -> throw new IllegalStateException("Unknown action " + sync.action() + ".");
    }
  }

  /**
   * Schedules removal of offline players that were loaded from the depot at startup.
   *
   * <p>For each offline player in the just-loaded queues:
   * <ul>
   *   <li>If {@code offlineSinceMs == 0}: the proxy was force-killed and no disconnect was
   *       recorded - the player is removed immediately since we cannot know how long they have
   *       been offline.</li>
   *   <li>If {@code offlineSinceMs > 0}: the remaining timeout is calculated from the stored
   *       disconnect time and the player is removed once that window expires.</li>
   * </ul>
   * Online players are skipped; the normal session lifecycle handles them.</p>
   */
  private void scheduleOfflineRemovals() {
    for (VelocityQueue<?> queue : queues.values()) {
      for (VelocityQueueEntry entry : queue.getEntries()) {
        if (isPlayerOnline(entry.getUniqueId())) {
          continue;
        }

        long offlineSinceMs = entry.getOfflineSinceMs();
        int timeoutSeconds = entry.getOfflineTimeoutSeconds();
        UUID uuid = entry.getUniqueId();

        long delayMs;
        if (offlineSinceMs == 0) {
          // Force-kill scenario: disconnect was never recorded, remove immediately.
          delayMs = 0;
        } else {
          long elapsedMs = System.currentTimeMillis() - offlineSinceMs;
          long remainingMs = (long) timeoutSeconds * 1000 - elapsedMs;
          delayMs = Math.max(0, remainingMs);
        }

        ScheduledTask task = server.getScheduler()
            .buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
              if (!isPlayerOnline(uuid)) {
                removePlayerEntirely(uuid);
              }
            })
            .delay(delayMs, TimeUnit.MILLISECONDS)
            .schedule();
        pendingTimeoutTasks.put(uuid, task);
      }
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
      for (VelocityQueue<?> queue : queues.values()) {
        server.getRedis().publish(VelocityQueueSync.statusChange(queue.getName(), queue.getServerStatus()));
        server.getRedis().publish(VelocityQueueSync.stateChange(queue.getName(), queue.getState()));
      }
    }
  }
}
