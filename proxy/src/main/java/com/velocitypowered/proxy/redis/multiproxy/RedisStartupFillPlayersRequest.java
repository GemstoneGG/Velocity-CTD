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
import java.util.List;

/**
 * Constructs a packet to update the currently available players when a proxy
 * starts up after redis has been running for a while.
 *
 * @param players A list of players to update the proxy on.
 * @param proxyIdToUpdate The proxy that just started up.
 */
public record RedisStartupFillPlayersRequest(List<MultiProxyHandler.RemotePlayerInfo> players, String proxyIdToUpdate) implements RedisPacket {
  public static final String ID = "redis-startup-fill-players";

  @Override
  public String getId() {
    return ID;
  }
}
