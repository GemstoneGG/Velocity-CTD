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

package com.velocityctd.proxy.queue;

import static com.velocityctd.proxy.queue.VelocityQueueManager.effectivePriority;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class VelocityQueueManagerTest {

  private static final int MINUTES_PER_INCREASE = 30;
  private static final int MAX_DYNAMIC_PRIORITY = 99;

  private static int effectiveAfterMinutes(int priority, long minutesQueued) {
    long now = TimeUnit.DAYS.toMillis(365);
    long joinedAt = now - TimeUnit.MINUTES.toMillis(minutesQueued);
    return effectivePriority(priority, joinedAt, now, MINUTES_PER_INCREASE, MAX_DYNAMIC_PRIORITY);
  }

  @Test
  void keepsPriorityBeforeFirstIncrease() {
    assertEquals(0, effectiveAfterMinutes(0, 29));
  }

  @Test
  void addsOnePriorityPerInterval() {
    assertEquals(1, effectiveAfterMinutes(0, 30));
    assertEquals(20, effectiveAfterMinutes(0, TimeUnit.HOURS.toMinutes(10)));
    assertEquals(25, effectiveAfterMinutes(5, TimeUnit.HOURS.toMinutes(10)));
  }

  @Test
  void capsPriorityGainedFromWaiting() {
    assertEquals(MAX_DYNAMIC_PRIORITY, effectiveAfterMinutes(0, TimeUnit.DAYS.toMinutes(120)));
    assertEquals(MAX_DYNAMIC_PRIORITY, effectiveAfterMinutes(98, TimeUnit.HOURS.toMinutes(1)));
  }

  @Test
  void keepsPrioritiesAboveTheCapUntouched() {
    assertEquals(150, effectiveAfterMinutes(150, TimeUnit.DAYS.toMinutes(120)));
    assertEquals(MAX_DYNAMIC_PRIORITY, effectiveAfterMinutes(MAX_DYNAMIC_PRIORITY, TimeUnit.DAYS.toMinutes(120)));
  }

  @Test
  void keepsPriorityForUnknownOrFutureJoinTimes() {
    long now = TimeUnit.DAYS.toMillis(365);
    assertEquals(5, effectivePriority(5, 0, now, MINUTES_PER_INCREASE, MAX_DYNAMIC_PRIORITY));
    assertEquals(5, effectivePriority(5, now + 1, now, MINUTES_PER_INCREASE, MAX_DYNAMIC_PRIORITY));
  }

  @Test
  void keepsPriorityForNonPositiveInterval() {
    long now = TimeUnit.DAYS.toMillis(365);
    long joinedAt = now - TimeUnit.DAYS.toMillis(1);
    assertEquals(5, effectivePriority(5, joinedAt, now, 0, MAX_DYNAMIC_PRIORITY));
    assertEquals(5, effectivePriority(5, joinedAt, now, -1, MAX_DYNAMIC_PRIORITY));
  }
}
