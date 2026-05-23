/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

import java.time.Duration;

/**
 * Represents a {@link Queue}'s ETA tracker. This class may be used to calculate a player's ETA by their position.
 */
public interface EtaTracker {

  /**
   * Computes the ETA for the given queue position.
   *
   * @param positionInQueue the 1-based queue position
   * @return the estimated wait time, never negative
   */
  Duration calculateEta(int positionInQueue);
}
