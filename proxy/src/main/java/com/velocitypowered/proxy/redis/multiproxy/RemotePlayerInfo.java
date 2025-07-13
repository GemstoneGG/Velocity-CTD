/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

/**
 * Stores information about a remote player connected to a different proxy.
 * Used to track the player's UUID, name, and the server they are connected to
 * within the multi-proxy network.
 */
public final class RemotePlayerInfo implements Serializable {

  /**
   * The ID of the proxy this player is currently connected to.
   */
  private final String proxyId;

  /**
   * The unique identifier (UUID) of the player.
   */
  private final UUID uuid;

  /**
   * The name of the player.
   */
  private final String name;

  /**
   * A mapping of server names to queue priority values for this player.
   *
   * <p>Higher values may indicate higher priority access to queues on the corresponding server.</p>
   */
  private final Map<String, Integer> queuePriority;

  /**
   * The name of the server the player is currently connected to.
   *
   * <p>This may be {@code null} if the player is not connected to any server.</p>
   */
  private String serverName = null;

  /**
   * Indicates whether the player is currently in the process of being transferred
   * between proxies.
   */
  private boolean beingTransferred = false;

  /**
   * Whether the player can bypass full queues entirely (e.g., join even when the queue is full).
   */
  private final boolean fullQueueBypass;

  /**
   * Whether the player can bypass regular queues (e.g., skip waiting entirely).
   */
  private final boolean queueBypass;

  /**
   * Constructs a new {@code RemotePlayerInfo} with the specified UUID and name.
   *
   * @param proxyId the ID of the proxy the player is connected to
   * @param uuid the UUID of the player
   * @param name the player's username
   * @param queuePriority a map of server names to queue priority values
   * @param fullQueueBypass whether the player can bypass a full queue entirely
   * @param queueBypass whether the player can bypass regular queue restrictions
   */
  public RemotePlayerInfo(final String proxyId, final UUID uuid, final String name, final Map<String, Integer> queuePriority,
                          final boolean fullQueueBypass,
                          final boolean queueBypass) {
    this.proxyId = proxyId;
    this.uuid = uuid;
    this.name = name;
    this.queuePriority = queuePriority;
    this.fullQueueBypass = fullQueueBypass;
    this.queueBypass = queueBypass;
  }

  /**
   * Gets the unique identifier (UUID) of the player.
   *
   * @return the player's UUID
   */
  public UUID getUuid() {
    return uuid;
  }

  /**
   * Checks whether the player is allowed to bypass regular queue restrictions.
   *
   * @return {@code true} if the player can skip the queue, {@code false} otherwise
   */
  public boolean isQueueBypass() {
    return queueBypass;
  }

  /**
   * Gets the player's name.
   *
   * @return the player's username
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the player's queue priorities for each server.
   *
   * @return a map of server names to integer queue priority values
   */
  public Map<String, Integer> getQueuePriority() {
    return queuePriority;
  }

  /**
   * Checks whether the player is allowed to bypass full queues (e.g., join even when full).
   *
   * @return {@code true} if the player bypasses full queues, {@code false} otherwise
   */
  public boolean isFullQueueBypass() {
    return fullQueueBypass;
  }

  /**
   * Returns the server the player is currently connected to, or null.
   *
   * @return The server the player is currently connected to, or null.
   */
  public String getServerName() {
    if (serverName == null) {
      return "";
    }

    return serverName;
  }

  /**
   * Checks whether the player is currently in the process of being transferred
   * to another proxy in the network.
   *
   * @return {@code true} if the player is being transferred, {@code false} otherwise
   */
  public boolean isBeingTransferred() {
    return beingTransferred;
  }

  /**
   * Gets the player's username.
   *
   * @return the player's name
   */
  public String getUsername() {
    return this.name;
  }

  /**
   * Gets the ID of the proxy this player is currently associated with.
   *
   * @return the proxy ID
   */
  public String getProxyId() {
    return this.proxyId;
  }

  /**
   * Set the connected server name.
   *
   * @param serverName The server name.
   */
  public void setServerName(final String serverName) {
    this.serverName = serverName;
  }

  /**
   * Updates the player's transfer status.
   *
   * @param beingTransferred {@code true} if the player is being transferred to another proxy,
   *                         {@code false} otherwise
   */
  public void setBeingTransferred(final boolean beingTransferred) {
    this.beingTransferred = beingTransferred;
  }
}
