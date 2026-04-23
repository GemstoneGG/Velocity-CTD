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

package com.velocitypowered.proxy.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import java.util.Collections;
import java.util.Map;

/**
 * A fake {@link CommandSource}.
 */
public class MockCommandSource implements CommandSource {

  /**
   * Singleton instance of the mock command source.
   */
  public static final CommandSource INSTANCE = new MockCommandSource();

  /**
   * Always returns {@link Tristate#UNDEFINED} for any permission query.
   *
   * @param permission the permission string to check
   * @return {@link Tristate#UNDEFINED}
   */
  @Override
  public Tristate getPermissionValue(String permission) {
    return Tristate.UNDEFINED;
  }

  /**
   * Always returns an empty map.
   *
   * @return {@link Collections#emptyMap()}
   */
  @Override
  public Map<String, Boolean> getPermissionMap() {
    return Collections.emptyMap();
  }
}
