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

package com.velocityctd.proxy.queue.util;

import static com.velocityctd.api.queue.QueueState.PAUSED;
import static com.velocityctd.api.queue.ServerStatus.FULL;

import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocityctd.proxy.queue.VelocityQueueEntry;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates {@link Component}s used by the queue system.
 */
public class QueueComponents {

  private QueueComponents() {
  }

  /**
   * Creates the action bar component shown to the player at their current position.
   */
  public static @Nullable Component createActionbarComponent(@NotNull VelocityQueueEntry entry) {
    VelocityQueue queue = entry.getQueue();
    Integer position = queue.getPosition(entry.getUniqueId()).orElse(null);
    if (position == null) {
      return null;
    }

    if (entry.isQueueBypass()) {
      return Component.translatable("velocity.queue.player-status.bypass", NamedTextColor.YELLOW);
    } else if (queue.getServerStatus() == FULL && !entry.isFullBypass()) {
      return Component.translatable("velocity.queue.player-status.full", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(queue.getName()),
              formatSeconds(queue.calculateEta(position)));
    } else if (entry.isWaitingForConnection()) {
      return Component.translatable("velocity.queue.player-status.connecting", NamedTextColor.YELLOW)
          .arguments(Component.text(queue.getName()));
    } else if (queue.getState() == PAUSED) {
      return Component.translatable("velocity.queue.player-status.paused", NamedTextColor.YELLOW);
    } else if (queue.getServerStatus().isActive()) {
      return Component.translatable("velocity.queue.player-status.online", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(queue.getName()),
              formatSeconds(queue.calculateEta(position)));
    } else {
      return Component.translatable("velocity.queue.player-status.offline", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(queue.getName()));
    }
  }

  /**
   * Formats a number of seconds as a component.
   *
   * @param inputSeconds the number of seconds
   * @return the time formatted as a component
   */
  private static Component formatSeconds(long inputSeconds) {
    long days = TimeUnit.SECONDS.toDays(inputSeconds);
    long hours = (TimeUnit.SECONDS.toHours(inputSeconds) - (days * 24L));
    long minutes = (TimeUnit.SECONDS.toMinutes(inputSeconds)
        - (TimeUnit.SECONDS.toHours(inputSeconds) * 60));

    Component output = Component.empty();
    if (days != 0) {
      output = output.append(formatTimeUnit("day", days));
    }

    if (hours != 0) {
      output = output.append(formatTimeUnit("hour", hours));
    }

    if (minutes != 0) {
      output = output.append(formatTimeUnit("minute", minutes));
    }

    long seconds = (TimeUnit.SECONDS.toSeconds(inputSeconds)
        - (TimeUnit.SECONDS.toMinutes(inputSeconds) * 60));

    return output.append(formatTimeUnit("second", seconds));
  }

  /**
   * Formats a time value as a component.
   *
   * @param name  the name of the time unit
   * @param value the value of the time unit
   * @return the time formatted as a component
   */
  private static Component formatTimeUnit(String name, long value) {
    String key = "velocity.queue.time." + name + (value == 1 ? "" : "s");
    return Component.translatable(key).arguments(Component.text(String.valueOf(value)));
  }
}
