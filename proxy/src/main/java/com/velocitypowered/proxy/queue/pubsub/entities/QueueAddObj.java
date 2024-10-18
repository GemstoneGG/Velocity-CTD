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
 * The {@code QueueAddObj} class represents an object that stores information about a player being
 * added to a queue for a specific server, including their unique identifier, the server name, and
 * their priority in the queue.
 * <p>
 * This object is used in the Redis Pub/Sub system to queue players for servers.
 * </p>
 */
public class QueueAddObj {

  private final String type = "QueueAddObj";
  private final String serverName;
  private final String playerUuid;
  private final int priority;

  /**
   * Constructs a new {@code QueueAddObj} with the specified player's UUID, server name, and priority.
   *
   * @param playerUuid the UUID of the player to be added to the queue, represented as a {@link String}.
   * @param serverName the name of the server to which the player is being queued.
   * @param priority the priority level for the player in the queue; higher values indicate higher priority.
   */
  public QueueAddObj(String playerUuid, String serverName, int priority) {
    this.playerUuid = playerUuid;
    this.serverName = serverName;
    this.priority = priority;
  }

  public String getServerName() {
    return serverName;
  }

  public String getPlayerUuid() {
    return playerUuid;
  }

  public int getPriority() {
    return priority;
  }
}
