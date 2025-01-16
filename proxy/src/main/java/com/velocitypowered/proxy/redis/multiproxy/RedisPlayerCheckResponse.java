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

/**
 * Represents a response to a Redis-based player check request.
 *
 * <p>This response indicates whether a specified player is currently online on the proxy.
 *
 * @param playerUuid the UUID of the player being checked
 * @param online true if the player is online, false otherwise
 */
public record RedisPlayerCheckResponse(String playerUuid, boolean online) implements RedisPacket {

  @Override
  public String getId() {
    return "player_check_response";
  }
}
