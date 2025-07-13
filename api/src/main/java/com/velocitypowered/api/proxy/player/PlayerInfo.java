/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.proxy.player;

import java.util.UUID;

/**
 * A class that represents the necessary information from all players connected
 * to servers when Redis is enabled.
 */
public class PlayerInfo {

  /**
   * The username of the player.
   */
  private final String username;

  /**
   * The UUID of the player.
   */
  private final UUID uuid;

  /**
   * Creates a Player Info holder.
   *
   * @param username The username of the player.
   * @param uuid The UUID of the player.
   */
  public PlayerInfo(final String username, final UUID uuid) {
    this.username = username;
    this.uuid = uuid;
  }

  /**
   * Gets the username of the player.
   *
   * @return the player's username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Gets the UUID of the player.
   *
   * @return the player's UUID
   */
  public UUID getUuid() {
    return uuid;
  }
}
