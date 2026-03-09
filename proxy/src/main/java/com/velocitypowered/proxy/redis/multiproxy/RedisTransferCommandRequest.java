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
 * Constructs a redis packet that transfers a player to another proxy.
 *
 * @param requester The UUID of the sender of the command.
 * @param player The username of the player.
 * @param proxyId The proxy id to send the player to.
 * @param ip The ip of the new proxy.
 * @param port The port of the new proxy.
 */
public record RedisTransferCommandRequest(UUID requester, String player, String proxyId, String ip, int port) implements RedisPacket {

  /**
   * The unique Redis packet identifier for a proxy transfer request.
   */
  public static final String ID = "transfer-command-request";

  @Override
  public String getId() {
    return ID;
  }
}
