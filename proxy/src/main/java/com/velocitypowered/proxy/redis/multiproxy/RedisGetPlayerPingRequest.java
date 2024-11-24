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
 * This packet is used to indicate a request that a player wants to view someone's ping.
 *
 * @param commandSender The command source of the player that did /ping.
 * @param playerToCheck The UUID of the player that needs to be checked.
 */
public record RedisGetPlayerPingRequest(EncodedCommandSource commandSender, String playerToCheck) implements RedisPacket {
  public static final String ID = "get-player-ping";

  @Override
  public String getId() {
    return ID;
  }
}
