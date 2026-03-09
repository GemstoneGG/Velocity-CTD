/*
 * Copyright (C) 2018-2026 Velocity Contributors
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
import java.util.UUID;

/**
 * Sends a request to kick a player.
 *
 * @param player  the UUID of the player to kick
 * @param proxyId the ID of the proxy expected to perform the kick
 */
public record RedisKickPlayerRequest(UUID player, String proxyId) implements RedisPacket {

  /**
   * The Redis packet ID for this request type.
   */
  public static final String ID = "kick-player-request";

  @Override
  public String getId() {
    return ID;
  }
}
