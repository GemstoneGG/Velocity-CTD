/*
 * Copyright (C) 2018-2023 Velocity Contributors
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
  private final String proxyId;
  private final UUID uuid;
  private final String name;
  private final Map<String, Integer> queuePriority;
  private String serverName = null;
  private boolean beingTransferred = false;
  private final boolean fullQueueBypass;
  private final boolean queueBypass;

  /**
   * Constructs a new {@code RemotePlayerInfo} with the specified UUID and name.
   *
   * @param uuid the player's unique identifier
   * @param name the player's name
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

  public UUID getUuid() {
    return uuid;
  }

  public boolean isQueueBypass() {
    return queueBypass;
  }

  public String getName() {
    return name;
  }

  public Map<String, Integer> getQueuePriority() {
    return queuePriority;
  }

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

  public boolean isBeingTransferred() {
    return beingTransferred;
  }

  public String getUsername() {
    return this.name;
  }

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

  public void setBeingTransferred(final boolean beingTransferred) {
    this.beingTransferred = beingTransferred;
  }

}
