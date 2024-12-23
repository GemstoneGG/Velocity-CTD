/*
 * Copyright (C) 2020-2024 Velocity Contributors
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

package com.velocitypowered.proxy.queue.cache;

import java.util.UUID;

/**
 * The serializable queue entry.
 */
public class SerializableQueueEntry {

  private final UUID uuid;
  private final int connectionAttempts;
  private final boolean waitingForConnection;
  private final int priority;
  private final boolean fullBypass;
  private final boolean queueBypass;

  /**
   * Constructs a serializable queue entry.
   *
   * @param uuid The UUID.
   * @param connectionAttempts The connection attempts.
   * @param waitingForConnection Waiting for connection or not.
   * @param priority The priority.
   * @param fullBypass The full bypass.
   * @param queueBypass The queue bypass.
   */
  public SerializableQueueEntry(final UUID uuid, final int connectionAttempts, final boolean waitingForConnection,
                                final int priority, final boolean fullBypass, final boolean queueBypass) {
    this.uuid = uuid;
    this.connectionAttempts = connectionAttempts;
    this.waitingForConnection = waitingForConnection;
    this.priority = priority;
    this.fullBypass = fullBypass;
    this.queueBypass = queueBypass;
  }

  /**
   * Gets the UUID.
   *
   * @return The UUID.
   */
  public UUID getUuid() {
    return uuid;
  }

  /**
   * Gets the connection attempts.
   *
   * @return The connection attempts.
   */
  public int getConnectionAttempts() {
    return connectionAttempts;
  }

  /**
   * Gets waiting for connection.
   *
   * @return waiting for connection or not.
   */
  public boolean isWaitingForConnection() {
    return waitingForConnection;
  }

  /**
   * Gets the priority.
   *
   * @return The priority.
   */
  public int getPriority() {
    return priority;
  }

  /**
   * Gets if bypass or not.
   *
   * @return Bypass or not.
   */
  public boolean isFullBypass() {
    return fullBypass;
  }

  /**
   * Gets if queue bypass or not.
   *
   * @return Queue bypass or not.
   */
  public boolean isQueueBypass() {
    return queueBypass;
  }
}
