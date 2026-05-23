/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable data record used to create a new {@link QueueEntry}.
 *
 * @param uniqueId    the unique identifier of the player
 * @param username    the username of the player
 * @param priority    the queue priority (must be &ge; 0)
 * @param fullBypass  whether the player can bypass full-server limits
 * @param queueBypass whether the player can bypass the queue entirely
 */
public record QueueEntryData(
    @NotNull UUID uniqueId,
    @NotNull String username,
    int priority,
    boolean fullBypass,
    boolean queueBypass
) {

  /**
   * Validates that the priority is non-negative.
   */
  public QueueEntryData {
    if (priority < 0) {
      throw new IllegalArgumentException("Priority must be >= 0, got " + priority);
    }
  }
}
