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

package com.velocitypowered.proxy.config;

/**
 * Supported passthrough modes for ping passthrough.
 */
public enum PingPassthroughMode {

  /**
   * No passthrough is applied. The proxy's configured MOTD, player count,
   * and other values are always used.
   */
  DISABLED,

  /**
   * Only mod-related metadata (e.g., Forge/Fabric mod list) is forwarded
   * from the backend server to the client, if available.
   */
  MODS,

  /**
   * Only the server description (MOTD) from the backend server is forwarded.
   * All other ping components (player count, favicon, etc.) are provided by the proxy.
   */
  DESCRIPTION,

  /**
   * All components of the backend server's ping response are forwarded to the client.
   * This includes MOTD, player count, version, favicon, and mod metadata.
   */
  ALL
}
