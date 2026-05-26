/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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
import java.time.Duration;
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
    VelocityQueue<?> queue = entry.getQueue();
    Integer position = queue.getPosition(entry.getUniqueId()).orElse(null);
    if (position == null) {
      return null;
    }

    if (entry.isQueueBypass()) {
      return Component.translatable("velocity.queue.player-status.bypass", NamedTextColor.YELLOW);
    } else if (queue.getServerStatus() == FULL && !entry.isFullBypass()) {
      return Component.translatable("velocity.queue.player-status.full-eta", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(queue.getName()),
              formatEta(queue, position));
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
              formatEta(queue, position));
    } else {
      return Component.translatable("velocity.queue.player-status.offline", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(queue.getName()));
    }
  }

  /**
   * Formats the ETA for the given position as a {@link Component} if the queue's
   * ETA tracker is available, or a text component with the string {@code "Unknown"} otherwise.
   */
  private static Component formatEta(VelocityQueue<?> queue, int position) {
    return queue.getEtaTracker()
        .map(t -> t.calculateEta(position))
        .map(QueueComponents::formatTime)
        .orElseGet(() -> Component.text("Unknown"));
  }

  private static Component formatTime(Duration duration) {
    long days = duration.toDaysPart();
    long hours = duration.toHoursPart();
    long minutes = duration.toMinutesPart();
    long seconds = duration.toSecondsPart();

    Component output = Component.empty();
    if (days != 0) {
      output = output.append(formatTimeUnit(
          days == 1
              ? "velocity.queue.time.day"
              : "velocity.queue.time.days",
          days
      ));
    }

    if (hours != 0) {
      output = output.append(formatTimeUnit(
          hours == 1
              ? "velocity.queue.time.hour"
              : "velocity.queue.time.hours",
          hours
      ));
    }

    if (minutes != 0) {
      output = output.append(formatTimeUnit(
          minutes == 1
              ? "velocity.queue.time.minute"
              : "velocity.queue.time.minutes",
          minutes
      ));
    }

    return output.append(formatTimeUnit(
        seconds == 1
            ? "velocity.queue.time.second"
            : "velocity.queue.time.seconds",
        seconds
    ));
  }

  /**
   * Formats a time value as a component.
   *
   * @param key   the translation key of the time unit
   * @param value the value of the time unit
   * @return the time formatted as a component
   */
  private static Component formatTimeUnit(String key, long value) {
    return Component.translatable(key).arguments(Component.text(String.valueOf(value)));
  }
}
