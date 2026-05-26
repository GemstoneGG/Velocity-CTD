/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

/**
 * Enumerates the status of a backend server as seen by the queue system.
 */
public enum ServerStatus {

  /**
   * The server is unreachable or has failed to respond. No players will be transferred.
   */
  OFFLINE,

  /**
   * The server responded but is in a warmup period. Players remain queued until the delay elapses.
   */
  WAITING,

  /**
   * The server is online and accepting player connections.
   */
  ONLINE,

  /**
   * The server is online but at capacity. Only players with the full-bypass permission
   * will be transferred.
   */
  FULL;

  /**
   * Returns {@code true} if the server is reachable and eligible for player transfers.
   * This covers both {@link #ONLINE} and {@link #FULL}.
   *
   * @return {@code true} for {@code ONLINE} and {@code FULL}, {@code false} otherwise
   */
  public boolean isActive() {
    return this == ONLINE || this == FULL;
  }
}
