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
 * reconnects and is visible to every proxy in a cluster. Group-scoped values are keyed by
 * both the player and the logical group name: the same player can have one last-server
 * value per group, and a backend that is a member of two groups is recorded for both.</p>
 *
 * <p>The unscoped methods are retained for source compatibility and for simple setups
 * with a single group. Their default group-scoped counterparts delegate to them, so
 * existing implementations continue to compile and work without changes.</p>
 */
public interface LastServerStore {

  /**
   * Returns the last backend recorded for the player without a group scope.
   *
   * @param playerId the unique id of the player
   * @return the last backend name, or an empty optional when nothing was recorded
   */
  Optional<String> getLastServer(UUID playerId);

  /**
   * Returns the last backend the player connected to within the given group.
   *
   * <p>Implementations that do not distinguish groups may keep delegating to
   * {@link #getLastServer(UUID)}; the default does exactly that.</p>
   *
   * @param playerId  the unique id of the player
   * @param groupName the logical server group name
   * @return the last backend name, or an empty optional when nothing was recorded
   */
  default Optional<String> getLastServer(UUID playerId, String groupName) {
    return getLastServer(playerId);
  }

  /**
   * Records the backend a player has just connected to without a group scope.
   *
   * @param playerId   the unique id of the player
   * @param serverName the name of the backend server
   */
  void recordLastServer(UUID playerId, String serverName);

  /**
   * Records the backend a player has just connected to within the given group.
   *
   * <p>Values are recorded only for genuine successful connections. Implementations that
   * do not distinguish groups may keep delegating to
   * {@link #recordLastServer(UUID, String)}; the default does exactly that.</p>
   *
   * @param playerId   the unique id of the player
   * @param groupName  the logical server group name
   * @param serverName the name of the backend server
   */
  default void recordLastServer(UUID playerId, String groupName, String serverName) {
    recordLastServer(playerId, serverName);
  }
}
