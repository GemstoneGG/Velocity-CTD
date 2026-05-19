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

package com.velocitypowered.proxy.adventure;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.pointer.Pointer;

/**
 * Adventure {@link Pointer pointers} that describe Velocity audiences.
 *
 * <p>This is Velocity's in-house replacement for the audience-type pointer that was
 * previously sourced from the now-archived {@code adventure-platform-facet} library.
 * Keeping it here removes that dependency while retaining the metadata under
 * Velocity's own control.</p>
 */
public final class AudiencePointers {

  /**
   * A pointer describing which {@link Type kind of audience} its holder represents.
   */
  public static final Pointer<Type> TYPE = Pointer.pointer(Type.class, Key.key("velocity", "audience/type"));

  private AudiencePointers() {
  }

  /**
   * The kinds of audience distinguished by {@link AudiencePointers#TYPE}.
   */
  public enum Type {

    /**
     * A connected player.
     */
    PLAYER,

    /**
     * The proxy console.
     */
    CONSOLE
  }
}
