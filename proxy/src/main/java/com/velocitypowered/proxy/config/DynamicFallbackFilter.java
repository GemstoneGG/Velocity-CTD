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
 * Sends players to the first available fallback server, the least populated
 * fallback server, or the most populated fallback server.
 */
public enum DynamicFallbackFilter {

  /**
   * Acts like regular Velocity and sends the player to
   * the first available server on the fallbacks list.
   */
  FIRST_AVAILABLE,

  /**
   * Sends the player to the fallback server
   * with the least number of players.
   */
  LEAST_POPULATED,

  /**
   * Sends the player to the fallback server
   * with the most number of players.
   */
  MOST_POPULATED,
}
