/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

import java.util.UUID;

/**
 * Represents a player that is currently in a {@link Queue}.
 */
public interface QueueEntry {

  /**
   * Returns the unique identifier of the player.
   *
   * @return the player's UUID
   */
  UUID getUniqueId();

  /**
   * Returns the username of the player.
   *
   * @return the player's username
   */
  String getUsername();

  /**
   * Returns the priority of this player in the queue. Higher values mean higher priority,
   * i.e., the player is closer to the front of the queue.
   *
   * @return the queue priority
   */
  int getPriority();

  /**
   * Returns the number of connection attempts made for this player so far.
   *
   * @return the connection attempt count
   */
  int getConnectionAttempts();

  /**
   * Returns {@code true} if the player is currently in the process of being transferred
   * to the backend server.
   *
   * @return {@code true} if a transfer is in progress
   */
  boolean isWaitingForConnection();

  /**
   * Returns {@code true} if this player has the full-server bypass permission, allowing
   * them to be transferred even when the backend server is full.
   *
   * @return {@code true} if the player can bypass the full-server limit
   */
  boolean isFullBypass();

  /**
   * Returns {@code true} if this player has the queue-bypass permission, allowing
   * them to connect directly without being queued.
   *
   * @return {@code true} if the player can bypass the queue
   */
  boolean isQueueBypass();

  /**
   * Returns the 1-based position of this entry in its owning queue.
   *
   * <p>This value is kept up to date by the queue whenever entries are added or removed.</p>
   *
   * @return the current queue position, where 1 is at the front
   */
  int getPosition();

  /**
   * Returns the {@link Queue} that this entry belongs to.
   *
   * @return the owning queue
   */
  Queue getQueue();
}
