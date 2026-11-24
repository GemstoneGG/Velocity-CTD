/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the queue system for the proxy.
 *
 * <p>The queue manager maintains one {@link Queue} per registered backend server.
 * In a multi-proxy Redis setup, exactly one proxy acts as the <em>master</em> and
 * is responsible for running periodic transfer and server-ping tasks.</p>
 */
public interface QueueManager {

  /**
   * Reloads the queue manager, rescheduling internal tasks based on the latest configuration.
   */
  void reload();

  /**
   * Tears down the queue manager, cancelling all scheduled tasks and clearing all queues.
   */
  void teardown();

  /**
   * Returns {@code true} if this proxy is currently the master proxy responsible for
   * running transfer and server-ping tasks.
   *
   * <p>In memory mode this always returns {@code true}.</p>
   *
   * @return {@code true} if this is the master proxy
   */
  boolean isMasterProxy();

  /**
   * Returns the {@link Queue} for the given server name, creating it if it does not yet exist.
   *
   * @param serverName the name of the target server
   * @return the associated queue
   * @throws IllegalArgumentException if no server with that name is registered
   */
  @NotNull
  Queue getQueue(@NotNull String serverName);

  /**
   * Returns an immutable snapshot of all managed queues.
   *
   * @return all queues
   */
  @NotNull
  Collection<? extends Queue> getQueues();

  /**
   * Returns an immutable snapshot of the configured logical server groups.
   *
   * <p>Implementations that do not support server groups keep the empty default. A logical
   * group presents several backends as one queue destination; its name is not the name of
   * a registered backend.</p>
   *
   * @return the configured groups, or an empty collection when unsupported
   */
  @NotNull
  default Collection<? extends ServerGroup> getServerGroups() {
    return List.of();
  }

  /**
   * Returns the logical server group with the exact given name.
   *
   * <p>The default returns {@code null} for implementations that do not support groups.</p>
   *
   * @param groupName the logical group name
   * @return the group, or {@code null} when no such group exists or groups are unsupported
   */
  @Nullable
  default ServerGroup getGroup(@NotNull String groupName) {
    return null;
  }

  /**
   * Returns {@code true} if the given player is present in any queue.
   *
   * @param uniqueId the unique id of the player to check
   * @return {@code true} if the player is queued
   */
  default boolean isQueued(@NotNull UUID uniqueId) {
    return getQueueFor(uniqueId) != null;
  }

  /**
   * Returns {@code true} if the given player is present in any queue.
   *
   * @param player the player to check
   * @return {@code true} if the player is queued
   */
  default boolean isQueued(@NotNull Player player) {
    return isQueued(player.getUniqueId());
  }

  /**
   * Returns the queue the given player is currently in, or {@code null} if the player
   * is not queued in any queue.
   *
   * @param uniqueId the unique id of the player to look up
   * @return the queue, or {@code null}
   */
  @Nullable
  Queue getQueueFor(@NotNull UUID uniqueId);

  /**
   * Returns the queue the given player is currently in, or {@code null} if the player
   * is not queued in any queue.
   *
   * @param player the player to look up
   * @return the queue, or {@code null}
   */
  @Nullable
  default Queue getQueueFor(@NotNull Player player) {
    return getQueueFor(player.getUniqueId());
  }

  /**
   * Validates eligibility and adds the given player to the shared queue for a logical group.
   *
   * <p>The default throws {@link UnsupportedOperationException} so implementations that
   * only queue for physical backends remain source compatible.</p>
   *
   * @param player    the player to queue
   * @param groupName the logical group name
   * @throws UnsupportedOperationException when server groups are not supported
   */
  default void queue(@NotNull Player player, @NotNull String groupName) {
    throw new UnsupportedOperationException("Server groups are not supported by this queue manager.");
  }

  /**
   * Validates eligibility and adds the given player to the queue for the specified server.
   *
   * <p>This method checks bypass permissions, multi-queue restrictions, paused state,
   * and version compatibility before enqueueing the player.</p>
   *
   * @param player the player to queue
   * @param server the target server
   */
  void queue(@NotNull Player player, @NotNull RegisteredServer server);

  /**
   * Immediately removes the given player from all queues they are currently in.
   *
   * @param uniqueId the unique id of the player to remove
   */
  void removePlayerEntirely(@NotNull UUID uniqueId);

  /**
   * Immediately removes the given player from all queues they are currently in.
   *
   * @param player the player to remove
   */
  default void removePlayerEntirely(@NotNull Player player) {
    removePlayerEntirely(player.getUniqueId());
  }
}
