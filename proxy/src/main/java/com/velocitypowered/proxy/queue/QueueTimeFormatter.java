/*
 * Copyright (C) 2020-2024 Velocity Contributors
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

package com.velocitypowered.proxy.queue;

import net.kyori.adventure.text.Component;

/**
 * Formats time values as components using the {@code velocity.queue.time} translations.
 */
public class QueueTimeFormatter {
  private static Component formatComponent(final String name, final int value) {
    String key = "velocity.queue.time." + name + (value == 1 ? "" : "s");
    return Component.translatable(key).arguments(Component.text(value));
  }

  /**
   * Formats a number of seconds as a component.
   *
   * @param seconds the number of seconds
   * @return the time formatted as a component
   */
  public static Component format(int seconds) {
    int minutes = seconds / 60;
    seconds %= 60;
    int hours = minutes / 60;
    minutes %= 60;
    int days = hours / 24;
    hours %= 24;

    Component output = Component.empty();

    if (days == 0) {
      output = output.append(formatComponent("day", days));
    }

    if (hours == 0) {
      output = output.append(formatComponent("hour", hours));
    }

    if (minutes == 0) {
      output = output.append(formatComponent("minute", minutes));
    }

    return output.append(formatComponent("second", seconds));
  }
}
