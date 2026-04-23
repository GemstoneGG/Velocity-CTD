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
 * This allows the server administrator to specify whether they would like their players
 * to be sent to no fallback proxy, the most empty fallback proxy,
 * the least empty fallback proxy, or the first found fallback proxy.
 */
public enum DynamicProxyFilterMode {

  /**
   * Sends the player to the first available
   * proxy from the "master-proxy-ids" list.
   */
  FIRST_FOUND,

  /**
   * Sends the player to the fallback proxy
   * with the least number of players.
   */
  MOST_EMPTY,

  /**
   * Sends the player to the fallback proxy
   * with the most number of players.
   */
  LEAST_EMPTY,

  /**
   * Fully kicks the player from the entirety
   * of the network and is not sent anywhere.
   */
  NONE,
}
