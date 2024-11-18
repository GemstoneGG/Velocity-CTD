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
 * Request to update the queued server name for
 * {@link com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler.RemotePlayerInfo}.
 *
 * @param player The UUID of the player.
 * @param server The server the player is queued to.
 */
public record RedisPlayerSetQueuedServerRequest(UUID player, String server) implements RedisPacket {
  public static final String ID = "set-queued-server";

  @Override
  public String getId() {
    return ID;
  }
}
