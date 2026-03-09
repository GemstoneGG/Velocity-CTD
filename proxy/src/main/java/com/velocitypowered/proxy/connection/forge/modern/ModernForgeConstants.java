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

package com.velocitypowered.proxy.connection.forge.modern;

/**
 * Constants for use with Modern Forge systems.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class ModernForgeConstants {

  /**
   * The token used to identify modern Forge connections in the server address.
   *
   * <p>Modern Forge clients append this token (e.g., {@code \0FORGE} or {@code \0FORGE14}) to the
   * hostname field in the login handshake to indicate their presence and optionally specify a
   * handshake version.</p>
   *
   * <p>This token allows the proxy to properly interpret and forward modern Forge login attempts.</p>
   */
  public static final String MODERN_FORGE_TOKEN = "FORGE";
}
