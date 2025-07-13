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

package com.velocitypowered.proxy.plugin.virtual;

/**
 * A singleton plugin object that represents the Velocity proxy itself.
 */
public final class VelocityVirtualPlugin {

  /**
   * The singleton instance of the Velocity virtual plugin.
   *
   * <p>This instance is used to represent the core Velocity proxy in contexts where a plugin
   * object is required.</p>
   */
  @SuppressWarnings("InstantiationOfUtilityClass")
  public static final VelocityVirtualPlugin INSTANCE = new VelocityVirtualPlugin();

  private VelocityVirtualPlugin() {
  }
}
