/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores the last backend a player played on, grouped by server group.
 *
 * <p>Implementations may use a shared store such as Redis so the information survives
 * reconnects and is visible to every proxy in a cluster.</p>
 */
public interface LastServerStore {

  /**
   * Returns the name of the backend the player last played on.
   *
   * @param playerId the unique id of the player
   * @return the last backend name, or an empty optional when nothing was recorded
   */
  Optional<String> getLastServer(UUID playerId);

  /**
   * Records the backend a player has just connected to.
   *
   * @param playerId   the unique id of the player
   * @param serverName the name of the backend server
   */
  void recordLastServer(UUID playerId, String serverName);
}
