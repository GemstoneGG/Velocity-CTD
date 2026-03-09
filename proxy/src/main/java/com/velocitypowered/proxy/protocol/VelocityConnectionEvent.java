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

package com.velocitypowered.proxy.protocol;

/**
 * Describes various events fired during a connection.
 */
public enum VelocityConnectionEvent {

  /**
   * Fired when packet compression is enabled on the connection.
   */
  COMPRESSION_ENABLED,

  /**
   * Fired when packet compression is disabled on the connection.
   */
  COMPRESSION_DISABLED,

  /**
   * Fired when encryption (e.g., using a shared secret) is enabled on the connection.
   */
  ENCRYPTION_ENABLED,

  /**
   * Fired when the protocol version of the connection changes (e.g., during handshake).
   */
  PROTOCOL_VERSION_CHANGED
}
