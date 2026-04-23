/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Represents the queue for a single backend server.
 *
 * <p>A queue maintains an ordered list of players waiting to be transferred
 * to the backend server. Players are ordered by priority (descending), then
 * by join time (ascending) within the same priority tier.</p>
 */
public interface Queue {

  /**
   * Returns the name of the backend server this queue targets.
   *
   * @return the server name
   */
  String getName();

  /**
   * Returns the registered server instance this queue targets.
   *
   * @return the backend server
   */
  RegisteredServer getServer();

  /**
   * Adds the given online player to this queue using their current permissions and priority.
   *
   * @param player the player to enqueue
   */
  void enqueue(@NotNull Player player);

  /**
   * Adds a player to this queue using explicit data.
   *
   * @param data the entry data describing the player to add
   */
  void enqueue(@NotNull QueueEntryData data);

  /**
   * Removes a player from the queue by their UUID.
   *
   * @param uniqueId the unique identifier of the player to remove
   */
  void dequeue(@NotNull UUID uniqueId);

  /**
   * Removes a player from the queue.
   *
   * @param player the player to remove
   */
  default void dequeue(@NotNull Player player) {
    dequeue(player.getUniqueId());
  }

  /**
   * Returns {@code true} if the given UUID is currently in this queue.
   *
   * @param uniqueId the player's UUID to check
   * @return {@code true} if the player is queued
   */
  boolean contains(@NotNull UUID uniqueId);

  /**
   * Returns {@code true} if the given player is currently in this queue.
   *
   * @param player the player to check
   * @return {@code true} if the player is queued
   */
  default boolean contains(@NotNull Player player) {
    return contains(player.getUniqueId());
  }

  /**
   * Returns the {@link QueueEntry} for the given player UUID, or {@code null} if not present.
   *
   * @param uniqueId the player's UUID
   * @return the queue entry, or {@code null}
   */
  @Nullable
  QueueEntry getEntry(@NotNull UUID uniqueId);

  /**
   * Returns an immutable snapshot of all entries currently in this queue, in order.
   *
   * @return an ordered, immutable collection of queue entries
   */
  @NotNull
  @Unmodifiable
  Collection<? extends QueueEntry> getEntries();

  /**
   * Returns the one-based position of the given player in the queue,
   * or {@link Optional#empty()} if the player is not present.
   *
   * @param uniqueId the player's UUID
   * @return the 1-based position, or {@link Optional#empty()}
   */
  Optional<Integer> getPosition(@NotNull UUID uniqueId);

  /**
   * Returns the one-based position of the given player in the queue,
   * or {@link Optional#empty()} if the player is not present.
   *
   * @param player the player
   * @return the 1-based position, or {@link Optional#empty()}
   */
  default Optional<Integer> getPosition(@NotNull Player player) {
    return getPosition(player.getUniqueId());
  }

  /**
   * Returns the number of players currently in this queue.
   *
   * @return the queue size
   */
  int size();

  /**
   * Returns the current status of the backend server.
   *
   * @return the server status
   */
  @NotNull
  ServerStatus getServerStatus();

  /**
   * Updates the status of the backend server.
   *
   * @param status the new server status
   */
  void setServerStatus(@NotNull ServerStatus status);

  /**
   * Returns the current operational state of this queue.
   *
   * @return the queue state
   */
  @NotNull
  QueueState getState();

  /**
   * Updates the operational state of this queue.
   *
   * @param state the new queue state
   */
  void setState(@NotNull QueueState state);

  /**
   * Clears all players from this queue and releases any held resources.
   */
  void teardown();
}
