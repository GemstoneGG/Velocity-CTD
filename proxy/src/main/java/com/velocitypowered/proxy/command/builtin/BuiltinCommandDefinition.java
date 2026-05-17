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

package com.velocitypowered.proxy.command.builtin;

import com.velocitypowered.api.command.BrigadierCommand;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface BuiltinCommandDefinition {

  /**
   * Builds and returns the command instance if enabled, or returns {@code null} if disabled (e.g. via configuration).
   *
   * @return the built command instance or {@code null} if disabled.
   */
  @Nullable
  BrigadierCommand build();

  /**
   * Returns the label for this built-in command, e.g. "alert" for /alert.
   *
   * @return The label for this built-in command.
   */
  @NonNull
  String label();

  /**
   * Returns aliases for this built-in command.
   *
   * @return The aliases for this built-in command.
   */
  @NonNull
  default List<String> aliases() {
    return Collections.emptyList();
  }
}
