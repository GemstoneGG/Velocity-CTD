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

package com.velocityctd.proxy.reconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LastServerTrackerTest {

  private final AtomicLong now = new AtomicLong(TimeUnit.HOURS.toNanos(1));
  private final LastServerTracker tracker = new LastServerTracker(now::get);
  private final UUID player = UUID.randomUUID();

  private void advanceMillis(long millis) {
    now.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
  }

  @Test
  void returnsRememberedServerWithinWindow() {
    tracker.remember(player, "survival", 120_000);
    advanceMillis(119_000);
    assertEquals(Optional.of("survival"), tracker.consume(player));
  }

  @Test
  void isUsedOnlyOnce() {
    tracker.remember(player, "survival", 120_000);
    assertEquals(Optional.of("survival"), tracker.consume(player));
    assertEquals(Optional.empty(), tracker.consume(player));
  }

  @Test
  void expiresAfterWindow() {
    tracker.remember(player, "survival", 120_000);
    advanceMillis(120_000);
    assertEquals(Optional.empty(), tracker.consume(player));
  }

  @Test
  void disabledWindowRemembersNothing() {
    tracker.remember(player, "survival", 0);
    assertEquals(Optional.empty(), tracker.consume(player));
  }

  @Test
  void latestServerWins() {
    tracker.remember(player, "survival", 120_000);
    tracker.remember(player, "creative", 120_000);
    assertEquals(Optional.of("creative"), tracker.consume(player));
  }

  @Test
  void prunesExpiredEntriesOfOtherPlayers() {
    for (int i = 0; i < 10; i++) {
      tracker.remember(UUID.randomUUID(), "survival", 1_000);
    }
    advanceMillis(2_000);
    tracker.remember(player, "survival", 1_000);
    assertEquals(1, tracker.size());
    assertTrue(tracker.consume(player).isPresent());
  }
}
