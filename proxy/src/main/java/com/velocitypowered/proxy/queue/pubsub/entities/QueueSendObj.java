/*
 * Copyright (C) 2018-2024 Velocity Contributors
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

package com.velocitypowered.proxy.queue.pubsub.entities;

/**
 * The {@code QueueSendObj} class represents an object for sending a player to a specified server
 * within the queue system, based on their unique identifier (UUID) and the server name.
 * <p>
 * This object is used in the Redis Pub/Sub system to handle the action of sending a player from
 * the queue to a target server.
 * </p>
 */
public class QueueSendObj {

  private final String type = "QueueSendObj";
  private final String uuid;
  private final String serverName;

  public QueueSendObj(String uuid, String serverName) {
    this.uuid = uuid;
    this.serverName = serverName;
  }

  public String getUuid() {
    return uuid;
  }

  public String getServerName() {
    return serverName;
  }
}
