/*
 * Copyright (C) 2024 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.redis.RedisPacket;
import java.util.Collections;
import java.util.Set;

/**
 * Represents a request to check whether specific players are currently online.
 *
 * <p>This request is sent via Redis to all proxies to determine the online status
 * of a list of players.
 *
 * @param playerUuids the set of player UUIDs to check
 */
public record RedisPlayerCheckRequest(Set<String> playerUuids) implements RedisPacket {

  /**
   * Constructs a new RedisPlayerCheckRequest.
   *
   * @param playerUuids the set of player UUIDs to check
   * @throws IllegalArgumentException if the set is null or empty
   */
  public RedisPlayerCheckRequest {
    if (playerUuids == null || playerUuids.isEmpty()) {
      throw new IllegalArgumentException("Player UUID set cannot be null or empty.");
    }
    playerUuids = Collections.unmodifiableSet(playerUuids);
  }

  @Override
  public String getId() {
    return "player_check_request";
  }
}
