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
 * Represents a packet sent when a proxy in a multi-proxy setup is shutting down.
 *
 * <p>This packet notifies other proxies in the network that a specific proxy instance
 * is shutting down, allowing them to handle the shutdown event accordingly, such as
 * updating the status of the proxy or re-balancing players.</p>
 *
 * @param proxyId the identifier of the proxy that is shutting down
 */
public record ShuttingDown(String proxyId) implements RedisPacket {
  public static final String ID = "shutting-down";

  @Override
  public String getId() {
    return ID;
  }
}
