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
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a packet sent when a player switches servers within a proxy in a multi-proxy setup.
 *
 * <p>This packet notifies other proxies in the network about the server change for a player, including
 * the player's unique identifier (UUID), the ID of the proxy, and the name of the server the player joined.
 * The {@code server} field may be {@code null} if the server information is unavailable.</p>
 *
 * @param proxyId the identifier of the proxy where the server change occurred
 * @param uuid the unique identifier of the player
 * @param server the name of the new server the player joined, or {@code null} if not specified
 */
public record RedisPlayerServerChange(String proxyId, UUID uuid, @Nullable String server) implements RedisPacket {
  public static final String ID = "player-server-change";

  @Override
  public String getId() {
    return ID;
  }
}
