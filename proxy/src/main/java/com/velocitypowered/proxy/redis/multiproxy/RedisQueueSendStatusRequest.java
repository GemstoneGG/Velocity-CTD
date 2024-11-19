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
 * Represents a packet to handle the connection of a user in a queue of a server.
 * This is only handled by the master-proxy-id as defined in the default velocity config.
 *
 * @param playerUuid The UUID of the player that's being added to the queue.
 * @param serverName The name of the server which the player is being de-queued for.
 * @param successfulTransfer Whether the transfer was successful or not.
 * @param id A unique ID linked to the status, so the other proxy can reply to the same message.
 */
public record RedisQueueSendStatusRequest(UUID playerUuid, String serverName,
                                          boolean successfulTransfer, UUID id) implements RedisPacket {
  public static final String ID = "redis-queue-send-status";

  @Override
  public String getId() {
    return ID;
  }
}
