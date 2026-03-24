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
import com.velocityctd.proxy.redis.impl.packet.VelocityActionBar;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Redis-aware extension of {@link VelocityQueueManager}.
 */
public final class RedisVelocityQueueManager extends VelocityQueueManager {

  /**
   * Redis key for the leader lock. Only one proxy can hold this lock at a time.
   */
  private static final String LEADER_LOCK_KEY = "velocity:queue:leader";

  /**
   * Leader lock lease duration in milliseconds. Must be less than
   * the scheduling period of the leader renewal task.
   */
  private static final long LEADER_LOCK_TTL_MS = 5000; // 5 seconds

  /**
   * How often to attempt to acquire or renew the leader lock (ms).
   * Should be slightly less than half the TTL to ensure timely renewal.
   */
  private static final long LEADER_ELECTION_INTERVAL_MS = 2000; // 2 seconds

  /**
   * The unique ID of this proxy instance.
   */
  private final String proxyId;

  /**
   * The current leader lock value (proxy ID) if this proxy is the leader, null otherwise.
   * Volatile for visibility across threads.
   */
  private volatile String currentLeader = null;

  /**
   * Task that periodically maintains leader status or attempts to acquire leadership.
   */
  private ScheduledTask leaderElectionTask;

  public RedisVelocityQueueManager(final @NotNull VelocityServer server) {
    super(server);
    this.proxyId = server.getProxyId();
  }

  @Override
  protected void preInitialize() {
    loadFromRedis();
  }

  @Override
  protected void postInitialize() {
    // Start the leader election/renewal task
    leaderElectionTask = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::performLeaderElection)
        .delay(0, TimeUnit.MILLISECONDS)
        .repeat(LEADER_ELECTION_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .schedule();

    server.getRedis().addReconnectListener(() ->
        server.getScheduler()
            .buildTask(VelocityVirtualPlugin.INSTANCE, this::reloadFromRedis)
            .schedule()
    );
  }

  @Override
  public boolean isMasterProxy() {
    // Fast path: if we already hold the leader lock, we're master
    if (proxyId.equals(currentLeader)) {
      return true;
    }

    // Slow path: try to acquire the lock (atomic operation in Redis)
    return tryAcquireLeaderLock();
  }

  /**
   * Attempts to acquire the Redis leader lock using atomic SET NX EX.
   * Only one proxy can succeed at a time.
   *
   * @return true if this proxy successfully acquired the lock, false otherwise
   */
  private boolean tryAcquireLeaderLock() {
    try {
      RedisPubSubCommands<String, String> sync = server.getRedis().getSyncPublisher();
      // Use SET with NX (only set if not exists) and EX (expiration in seconds)
      String result = sync.set(LEADER_LOCK_KEY, proxyId, 
          new io.lettuce.core.SetArgs().nx().px(LEADER_LOCK_TTL_MS));

      if ("OK".equals(result)) {
        // Successfully acquired the lock
        currentLeader = proxyId;
        return true;
      }

      // Lock held by someone else
      return false;
    } catch (Exception e) {
      server.getLogger().warn("Failed to acquire leader lock from Redis", e);
      return false;
    }
  }

  /**
   * Renews the leader lock if this proxy is currently the leader.
   * Uses Lua script or multiple commands to ensure atomicity.
   */
  private void renewLeaderLock() {
    if (!proxyId.equals(currentLeader)) {
      return;
    }

    try {
      RedisPubSubCommands<String, String> sync = server.getRedis().getSyncPublisher();
      // Check if we still hold the lock, then update expiration
      // Use a Lua script for atomicity: if value equals our ID, set new expiration
      String script =
          "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
          "  return redis.call('PEXPIRE', KEYS[1], ARGV[2]) " +
          "else " +
          "  return 0 " +
          "end";

      Long result = sync.eval(script, 
          ScriptOutputType.INTEGER,
          LEADER_LOCK_KEY, proxyId, String.valueOf(LEADER_LOCK_TTL_MS));

      if (result == 0) {
        // Lost the lock (someone else took it or it expired and was taken)
        currentLeader = null;
      }
    } catch (Exception e) {
      server.getLogger().warn("Failed to renew leader lock from Redis", e);
    }
  }

  /**
   * Periodically called task that either acquires or renews the leader lock.
   * This is the main leader election mechanism.
   */
  private void performLeaderElection() {
    if (currentLeader == null) {
      // We're not leader, try to become one
      tryAcquireLeaderLock();
    } else {
      // We are leader, renew our lock
      renewLeaderLock();
    }
  }

  @Override
  public void teardown() {
    super.teardown();
    if (leaderElectionTask != null) {
      leaderElectionTask.cancel();
    }
    // Release the lock if we are leader
    if (proxyId.equals(currentLeader)) {
      try {
        RedisPubSubCommands<String, String> sync = server.getRedis().getSyncPublisher();
        if (sync != null) {
          // Only delete if we still own it (using Lua script for safety)
          String script =
              "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
              "  return redis.call('DEL', KEYS[1]) " +
              "else " +
              "  return 0 " +
              "end";
          sync.eval(script,
              ScriptOutputType.INTEGER,
              LEADER_LOCK_KEY, proxyId);
        }
      } catch (Exception ignored) {
        // Redis might be down, ignore
      }
    }
  }

  @Override
  protected boolean isPlayerOnline(final UUID uuid) {
    return server.getRedis().getPlayerService().isPlayerOnline(uuid);
  }

  @Override
  protected RedisVelocityQueue createQueue(final VelocityRegisteredServer rs, final QueueState state) {
    return new RedisVelocityQueue(server, this, rs, state);
  }

  @Override
  protected void sendActionBar(final VelocityQueueEntry entry) {
    final Component component = QueueComponents.createActionbarComponent(entry);
    if (component != null) {
      new VelocityActionBar(entry.getUniqueId(), component).publish();
    }
  }

  /**
   * Applies a {@link VelocityQueueSync} packet received from another proxy to the local
   * in-memory queue.
   *
   * @param packet the incoming sync packet
   */
  public void handleSync(final @NotNull VelocityQueueSync packet) {
    final VelocityQueueSync.Payload p = packet.getPayload();
    if (p == null) {
      return;
    }

    final RedisVelocityQueue queue;
    try {
      queue = (RedisVelocityQueue) getQueue(p.serverName());
    } catch (IllegalArgumentException ignored) {
      return; // unknown server
    }

    switch (p.action()) {
      case ENQUEUE -> queue.applyEnqueue(p);
      case DEQUEUE -> queue.applyDequeue(p.playerUuid());
      case STATE_CHANGE -> queue.applyStateChange(p.newState());
      case STATUS_CHANGE -> queue.applyStatusChange(p.newStatus());
      case WAITING_CHANGE -> queue.applyWaitingChange(p);
      default -> throw new IllegalStateException("Unknown action " + p.action() + ".");
    }
  }

  /**
   * Loads persisted queue state from the Redis depot.
   * Called at startup ({@link #preInitialize()}) and on reconnect ({@link #reloadFromRedis()}).
   */
  private void loadFromRedis() {
    final VelocityQueueDepotService service = server.getRedis().getQueueService();

    queues.clear();
    for (VelocityQueueDepotEntry entry : service.getAll()) {
      final VelocityRegisteredServer rs = server.getServer(entry.getUniqueId()).orElse(null);
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
        new VelocityQueueSync(VelocityQueueSync.Payload.statusChange(
            queue.getName(), queue.getServerStatus())).publish();
        new VelocityQueueSync(VelocityQueueSync.Payload.stateChange(
            queue.getName(), queue.getState())).publish();
      }
    }
  }
}
