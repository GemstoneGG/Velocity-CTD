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

import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;

/**
 * Formats time values as components using the {@code velocity.queue.time} translations.
 */
public class QueueTimeFormatter {
  private static Component formatComponent(final String name, final long value) {
    String key = "velocity.queue.time." + name + (value == 1 ? "" : "s");
    return Component.translatable(key).arguments(Component.text(value));
  }

  /**
   * Formats a number of seconds as a component.
   *
   * @param inputSeconds the number of seconds
   * @return the time formatted as a component
   */
  public static Component format(final long inputSeconds) {
    long days = TimeUnit.SECONDS.toDays(inputSeconds);
    long hours = (TimeUnit.SECONDS.toHours(inputSeconds) - (days * 24L));
    long minutes = (TimeUnit.SECONDS.toMinutes(inputSeconds)
                - (TimeUnit.SECONDS.toHours(inputSeconds) * 60));


    Component output = Component.empty();
    if (days != 0) {
      output = output.append(formatComponent("day", days));
    }
    if (hours != 0) {
      output = output.append(formatComponent("hour", hours));
    }
    if (minutes != 0) {
      output = output.append(formatComponent("minute", minutes));
    }
    long seconds = (TimeUnit.SECONDS.toSeconds(inputSeconds)
            - (TimeUnit.SECONDS.toMinutes(inputSeconds) * 60));

    return output.append(formatComponent("second", seconds));
  }
}
