/*
 * Copyright (C) 2018-2023 Velocity Contributors
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
  private final String username;
  private final UUID uuid;

  /**
   * Creates a Player Info holder.
   *
   * @param username The username of the player.
   * @param uuid The UUID of the player.
   */
  public PlayerInfo(String username, UUID uuid) {
    this.username = username;
    this.uuid = uuid;
  }

  public String getUsername() {
    return username;
  }

  public UUID getUuid() {
    return uuid;
  }
}
