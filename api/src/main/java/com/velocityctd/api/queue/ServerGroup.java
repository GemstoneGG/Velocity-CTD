/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

import java.util.List;

/**
 * A logical group of backend servers presented to players as a single destination.
 *
 * <p>Players queue for the group rather than for an individual backend. When a player
 * reaches the front of the shared queue, the proxy chooses a backend according to the
 * group's selection strategy. A group name must not collide with a registered backend
 * name: physical queues take precedence and colliding groups are ignored.</p>
 *
 * <p>Last-server affinity is scoped to the group, so a player's remembered backend in
 * one group never leaks into another. Values are recorded only after a successful
 * connection to a backend that belongs to the group.</p>
 */
public interface ServerGroup {

  /**
   * Returns the name players use to queue for this group.
   *
   * @return the group name
   */
  String getName();

  /**
   * Returns the configured backend names in their configured order.
   *
   * @return an immutable list of backend names
   */
  List<String> getServers();

  /**
   * Returns the strategy used to choose a backend for a player.
   *
   * @return the selection strategy
   */
  SelectionStrategy getStrategy();

  /**
   * Returns the maximum number of players allowed on a backend in this group.
   *
   * <p>A value of {@code -1} or {@code 0} means that the group applies no limit of its
   * own; backend capacity is then determined by the backend's status alone. A positive
   * limit is advisory and best-effort: player counts may be slightly out of date across
   * proxies, and players with the full-server bypass permission may still be sent to a
   * backend at or above the limit. It is not a hard distributed capacity guarantee.</p>
   *
   * @return the per-backend player limit, or {@code -1}/{@code 0} for no limit
   */
  int getMaxPlayers();
}
