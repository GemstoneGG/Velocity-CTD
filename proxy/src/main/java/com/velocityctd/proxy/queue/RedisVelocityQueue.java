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

import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.ServerStatus;
import com.velocityctd.proxy.queue.redis.depot.VelocityQueueDepotEntry;
import com.velocityctd.proxy.queue.redis.packet.VelocityQueueSync;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.data.VelocityMessage;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Redis-aware extension of {@link VelocityQueue}.
 */
public final class RedisVelocityQueue extends VelocityQueue<RedisVelocityQueueEntry> {

  private final VelocityRedis redis;

  /**
   * Creates a fresh Redis-backed queue for the given backend server.
   */
  public RedisVelocityQueue(VelocityServer server, VelocityQueueManager manager,
                            VelocityRegisteredServer backend, QueueState initialState) {
    super(server, manager, backend, initialState);

    this.redis = server.getRedis();
  }

  /**
   * Creates a Redis-backed group queue with a shared display name and backend resolver.
   *
   * @param server          the proxy instance
   * @param manager         the owning queue manager
   * @param backend         a registered server used as a fallback target
   * @param initialState    the initial queue state
   * @param displayName     the group name shown to players
   * @param backendResolver resolves the target backend for a player and its full-bypass flag
   */
  public RedisVelocityQueue(VelocityServer server, VelocityQueueManager manager,
                            VelocityRegisteredServer backend, QueueState initialState,
                            String displayName,
                            BiFunction<UUID, Boolean, Optional<VelocityRegisteredServer>> backendResolver) {
    super(server, manager, backend, initialState, displayName, backendResolver);

    this.redis = server.getRedis();
  }

  /**
   * Reconstructs a Redis-backed queue from a persisted depot snapshot (cold-start or reconnect).
   *
   * <p>Entries are appended in their already-sorted depot order via
   * {@link #addEntryInternal(RedisVelocityQueueEntry)}, which bypasses priority-insertion and
   * any publish/persist side effects.</p>
   *
   * @param server  the proxy instance
   * @param manager the owning queue manager
   * @param backend the registered backend this queue represents
   * @param entry   the persisted depot snapshot
   */
  public RedisVelocityQueue(VelocityServer server, VelocityQueueManager manager,
                            VelocityRegisteredServer backend,
                            VelocityQueueDepotEntry entry) {
    super(server, manager, backend, entry.getState());

    this.redis = server.getRedis();
    restoreSnapshot(entry);
  }

  /**
   * Reconstructs a Redis-backed group queue from a persisted depot snapshot.
   *
   * <p>The logical group name and a name-based resolver are kept so the restored queue keeps
   * working after {@code groupManager.rebuild()} replaces the runtime group object. The
   * snapshot's state, server status, entries and ordering are preserved verbatim.</p>
   *
   * @param server          the proxy instance
   * @param manager         the owning queue manager
   * @param backend         a registered member of the group used as a fallback target
   * @param entry           the persisted depot snapshot
   * @param displayName     the group name shown to players (also the depot key)
   * @param backendResolver resolves the target backend for a player and its full-bypass flag
   */
  public RedisVelocityQueue(VelocityServer server, VelocityQueueManager manager,
                            VelocityRegisteredServer backend, VelocityQueueDepotEntry entry,
                            String displayName,
                            BiFunction<UUID, Boolean, Optional<VelocityRegisteredServer>>
                                backendResolver) {
    super(server, manager, backend, entry.getState(), displayName, backendResolver);

    this.redis = server.getRedis();
    restoreSnapshot(entry);
  }

  /**
   * Applies a depot snapshot without emitting the usual publish/persist side effects.
   *
   * <p>Warmup timing is reseeded so a restored WAITING queue can still be promoted once its
   * configured delay elapses.</p>
   */
  private void restoreSnapshot(VelocityQueueDepotEntry entry) {
    // super.setServerStatus sets the field without triggering the publish override.
    super.setServerStatus(entry.getServerStatus());

    for (RedisVelocityQueueEntry queueEntry : entry.getEntries()) {
      queueEntry.setContext(server, this);
      // A snapshot can catch an entry mid-transfer. Clear the flag (and publish the reset)
      // so selectCandidate() can retry it instead of skipping it forever.
      queueEntry.resetWaitingForRetry();
      // Append in the depot's existing order without priority insertion or publish side effects.
      addEntryInternal(queueEntry);
    }

    if (entry.getServerStatus() == ServerStatus.WAITING) {
      VelocityQueueManager.LAST_TURNED_ONLINE_TIME.put(getName(), System.currentTimeMillis());
    }
  }

  @Override
  protected RedisVelocityQueueEntry createEntry(@NotNull QueueEntryData data) {
    return new RedisVelocityQueueEntry(server, this, data);
  }

  @Override
  public void enqueue(@NotNull QueueEntryData data) {
    super.enqueue(data);

    redis.publish(VelocityQueueSync.enqueue(
        getName(), data.uniqueId(), data.username(), data.priority(),
        data.fullBypass(), data.queueBypass()
    ));
    persistAsync();
  }

  @Override
  public void dequeue(@NotNull UUID uniqueId) {
    super.dequeue(uniqueId);

    redis.publish(VelocityQueueSync.dequeue(getName(), uniqueId));
    persistAsync();
  }

  @Override
  public void setServerStatus(@NotNull ServerStatus status) {
    ServerStatus prev = getServerStatus();
    super.setServerStatus(status);

    if (getServerStatus() != prev) {
      redis.publish(VelocityQueueSync.statusChange(getName(), status));
      persistAsync();
    }
  }

  @Override
  public void setState(@NotNull QueueState state) {
    QueueState prev = getState();
    super.setState(state);

    if (getState() != prev) {
      redis.publish(VelocityQueueSync.stateChange(getName(), state));
      persistAsync();
    }
  }

  @Override
  public void teardown() {
    super.teardown();

    persistAsync();
  }

  @Override
  void removeEntry(VelocityQueueEntry entry) {
    super.removeEntry(entry);

    redis.publish(VelocityQueueSync.dequeue(getName(), entry.getUniqueId()));
    persistAsync();
  }

  @Override
  public void broadcastMessage(@NotNull Function<RedisVelocityQueueEntry, Component> componentFn) {
    for (RedisVelocityQueueEntry entry : getEntries()) {
      Component msg = componentFn.apply(entry);
      redis.publish(new VelocityMessage(entry.getUniqueId(), msg));
    }
  }

  /**
   * Applies an ENQUEUE sync received from another proxy.
   *
   * <p>Calls {@code super.enqueue()} which uses the overridden {@link #createEntry} factory
   * to produce a {@link RedisVelocityQueueEntry}, then inserts it by priority, without
   * triggering this class's publish override.</p>
   */
  @ApiStatus.Internal
  void applyEnqueue(@NotNull QueueEntryData data) {
    super.enqueue(data);
    // The master owns the depot snapshot: changes made on other proxies arrive only as sync
    // packets, so persist them here or they would be lost on the next reconnect.
    persistAsync();
  }

  /**
   * Applies a DEQUEUE sync received from another proxy.
   */
  @ApiStatus.Internal
  void applyDequeue(@NotNull UUID uuid) {
    super.dequeue(uuid);
    persistAsync();
  }

  /**
   * Applies a STATE_CHANGE sync received from another proxy.
   */
  @ApiStatus.Internal
  void applyStateChange(@NotNull QueueState newState) {
    super.setState(newState);
    persistAsync();
  }

  /**
   * Applies a STATUS_CHANGE sync received from another proxy.
   */
  @ApiStatus.Internal
  void applyStatusChange(@NotNull ServerStatus newStatus) {
    super.setServerStatus(newStatus);
    persistAsync();
  }

  /**
   * Applies a WAITING_CHANGE sync received from another proxy.
   */
  @ApiStatus.Internal
  void applyWaitingChange(@NotNull UUID uuid, boolean waitingForConnection, int connectionAttempts,
                          int updatedPriority, boolean updatedFullBypass, boolean updatedQueueBypass) {
    RedisVelocityQueueEntry entry = getEntry(uuid);
    if (entry != null) {
      entry.applyWaitingChangeFromPacket(
          waitingForConnection, connectionAttempts,
          updatedPriority, updatedFullBypass, updatedQueueBypass
      );
      persistAsync();
    }
  }

  /**
   * Applies an OFFLINE_CHANGE sync received from another proxy, updating the entry's
   * offline timestamp and timeout so the master's depot snapshot stays accurate.
   */
  @ApiStatus.Internal
  void applyOfflineChange(@NotNull UUID uuid, long offlineSinceMs, int offlineTimeoutSeconds) {
    VelocityQueueEntry entry = getEntry(uuid);
    if (entry != null) {
      entry.setOfflineFromSync(offlineSinceMs, offlineTimeoutSeconds);
      persistAsync();
    }
  }

  /**
   * Persists this queue's state to the Redis depot asynchronously.
   * Only executed when this proxy is currently the master.
   */
  private void persistAsync() {
    if (manager.isMasterProxy()) {
      CompletableFuture.runAsync(() -> server.getRedis().getQueueService().upsertQueue(this));
    }
  }
}
