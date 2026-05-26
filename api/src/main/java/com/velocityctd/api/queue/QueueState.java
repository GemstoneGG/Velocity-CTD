/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

/**
 * Represents the operational state of a {@link Queue}.
 */
public enum QueueState {

  /**
   * The queue is not active and does not process or accept players.
   */
  INACTIVE,

  /**
   * The queue is active and will transfer players to the backend server when it is available.
   */
  ACTIVE,

  /**
   * The queue is paused and will not transfer players until it is resumed.
   */
  PAUSED
}
