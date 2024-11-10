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
 * Represents a packet sent when a player joins a proxy in a multi-proxy setup.
 *
 * <p>This packet is used to inform other proxies in the network about the new player
 * and includes details such as the player's unique identifier (UUID), name, and
 * the ID of the proxy where the player joined.</p>
 *
 * @param player the joining player
 */
public record RedisPlayerJoinUpdate(MultiProxyHandler.RemotePlayerInfo player) implements RedisPacket {
  public static final String ID = "player-join";

  @Override
  public String getId() {
    return ID;
  }
}
