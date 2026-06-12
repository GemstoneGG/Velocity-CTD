/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.event.permission;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.Player;

/**
 * This event is fired when the effective permissions of a connected {@link Player} change, for
 * example when their groups or directly-assigned permissions are modified by a permission plugin.
 * Velocity will not wait on this event to finish firing.
 *
 * <p>This event requires the player's permission provider to report permission changes (such as
 * LuckPerms through Velocity-CTD's integration). It may be fired from an arbitrary thread, and rapid
 * successive changes for the same player may be coalesced into a single event.
 */
public final class PermissionsChangeEvent {

  /**
   * The player whose effective permissions changed.
   */
  private final Player player;

  /**
   * Constructs a new {@code PermissionsChangeEvent}.
   *
   * @param player the player whose permissions changed
   */
  public PermissionsChangeEvent(Player player) {
    this.player = Preconditions.checkNotNull(player, "player");
  }

  /**
   * Returns the player whose effective permissions changed.
   *
   * @return the player
   */
  public Player getPlayer() {
    return player;
  }

  @Override
  public String toString() {
    return "PermissionsChangeEvent{"
        + "player=" + player
        + '}';
  }
}
