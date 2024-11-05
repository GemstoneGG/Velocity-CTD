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
import java.util.UUID;

/**
 * Represents a packet sent when a player leaves a proxy in a multi-proxy setup.
 *
 * <p>This packet informs other proxies in the network that a player has disconnected
 * and includes the player’s unique identifier (UUID) and the ID of the proxy they left.</p>
 *
 * @param proxyId the identifier of the proxy the player left
 * @param uuid the unique identifier of the player
 */
public record PlayerLeaveUpdate(String proxyId, UUID uuid) implements RedisPacket {
  public static final String ID = "player-leave";

  @Override
  public String getId() {
    return ID;
  }
}
