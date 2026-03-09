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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Basic, common command messages.
 */
public class CommandMessages {

  /**
   * Indicates that the command is only available to players and not console or other sources.
   *
   * <p>Translation key: {@code velocity.command.players-only}</p>
   */
  public static final TranslatableComponent PLAYERS_ONLY = Component.translatable(
      "velocity.command.players-only", NamedTextColor.RED);

  /**
   * Indicates that the specified server could not be found.
   *
   * <p>Translation key: {@code velocity.command.server-does-not-exist}</p>
   */
  public static final TranslatableComponent SERVER_DOES_NOT_EXIST = Component.translatable(
      "velocity.command.server-does-not-exist", NamedTextColor.RED);

  /**
   * Indicates that multiple servers matched the specified input.
   *
   * <p>Translation key: {@code velocity.command.server-multiple-matches}</p>
   */
  public static final TranslatableComponent SERVER_MULTIPLE_MATCH = Component.translatable(
      "velocity.command.server-multiple-matches", NamedTextColor.RED);

  /**
   * Indicates that the specified player could not be found online.
   *
   * <p>Translation key: {@code velocity.command.player-not-found}</p>
   */
  public static final TranslatableComponent PLAYER_NOT_FOUND = Component.translatable(
      "velocity.command.player-not-found", NamedTextColor.RED);
}
