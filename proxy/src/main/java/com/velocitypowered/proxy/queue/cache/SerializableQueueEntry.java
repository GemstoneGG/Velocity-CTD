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

package com.velocitypowered.proxy.queue.cache;

import java.util.UUID;

/**
 * The serializable queue entry.
 *
 * @param uuid the unique identifier of the player
 * @param connectionAttempts the number of connection attempts made by the player
 * @param waitingForConnection whether the player is currently waiting for a connection
 * @param priority the queue priority level of the player
 * @param fullBypass whether the player can bypass the full server restriction
 * @param queueBypass whether the player can bypass the queue entirely
 */
public record SerializableQueueEntry(UUID uuid, int connectionAttempts, boolean waitingForConnection, int priority,
                                     boolean fullBypass, boolean queueBypass) {

  /**
   * Constructs a serializable queue entry.
   *
   * @param uuid                 The UUID.
   * @param connectionAttempts   The connection attempts.
   * @param waitingForConnection Waiting for connection or not.
   * @param priority             The priority.
   * @param fullBypass           The full bypass.
   * @param queueBypass          The queue bypass.
   */
  public SerializableQueueEntry {
  }

  /**
   * Gets the UUID.
   *
   * @return The UUID.
   */
  @Override
  public UUID uuid() {
    return uuid;
  }

  /**
   * Gets the connection attempts.
   *
   * @return The connection attempts.
   */
  @Override
  public int connectionAttempts() {
    return connectionAttempts;
  }

  /**
   * Gets waiting for connection.
   *
   * @return waiting for connection or not.
   */
  @Override
  public boolean waitingForConnection() {
    return waitingForConnection;
  }

  /**
   * Gets the priority.
   *
   * @return The priority.
   */
  @Override
  public int priority() {
    return priority;
  }

  /**
   * Gets if bypass or not.
   *
   * @return Bypass or not.
   */
  @Override
  public boolean fullBypass() {
    return fullBypass;
  }

  /**
   * Gets if queue bypass or not.
   *
   * @return Queue bypass or not.
   */
  @Override
  public boolean queueBypass() {
    return queueBypass;
  }
}
