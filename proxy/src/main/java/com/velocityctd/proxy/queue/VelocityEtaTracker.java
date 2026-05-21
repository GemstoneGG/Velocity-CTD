/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

import com.velocityctd.api.queue.EtaTracker;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.VelocityServer;
import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public final class VelocityEtaTracker implements EtaTracker {

  private static final int WINDOW_SIZE = 20;

  private final VelocityServer server;
  private final long[] leaveIntervalsMillis = new long[WINDOW_SIZE];

  private int sampleCount = 0;
  private int writeIndex = 0;
  private Integer lastOnline = null;
  private Integer lastMax = null;

  private Long lastLeaveMillis;

  VelocityEtaTracker(@NotNull VelocityServer server) {
    this.server = server;
  }

  synchronized void recordBackendPing(ServerPing.Players pingPlayers) {
    this.lastOnline = pingPlayers.getOnline();
    this.lastMax = pingPlayers.getMax();
  }

  synchronized void recordBackendPlayerLeave(long nowMillis) {
    if (lastLeaveMillis != null) {
      long intervalMillis = nowMillis - lastLeaveMillis;
      if (intervalMillis > 0) {
        pushInterval(intervalMillis);
      }
    }

    lastLeaveMillis = nowMillis;
  }

  /**
   * Resets the ETA tracker.
   */
  synchronized void reset() {
    this.lastOnline = null;
    this.lastMax = null;
    this.lastLeaveMillis = null;
  }

  /**
   * Computes the ETA for the given queue position.
   *
   * @param position the 1-based queue position
   * @return         the estimated wait time, never negative
   */
  @Override
  public synchronized Duration calculateEta(int position) {
    long sendDelayMillis = (long) (server.getConfiguration().getQueue().getSendDelay() * 1000.0);

    if (lastOnline == null || lastMax == null || lastMax <= 0) {
      return sendDelayEta(position, sendDelayMillis);
    }

    int availableNow = Math.max(0, lastMax - lastOnline);
    int deficit = Math.max(0, lastOnline - lastMax);

    int sendDelaySteps = Math.min(position, availableNow);
    int leaveSteps = (position - sendDelaySteps) + deficit;

    if (leaveSteps == 0) {
      // The player fits within the current free spots; no leaves required.
      return sendDelayEta(sendDelaySteps, sendDelayMillis);
    }

    long averageIntervalMillis = averageLeaveIntervalMillis();
    if (averageIntervalMillis <= 0) {
      // No leave samples yet - degrade gracefully to a flat per-position send-delay estimate.
      return sendDelayEta(position, sendDelayMillis);
    }

    long totalMillis = sendDelaySteps * sendDelayMillis + leaveSteps * averageIntervalMillis;
    return Duration.ofMillis(totalMillis);
  }

  /**
   * Returns the average windowed departure interval in milliseconds, or {@code 0} if no samples
   * have been recorded.
   */
  private long averageLeaveIntervalMillis() {
    if (sampleCount == 0) {
      return 0;
    }

    long sum = 0;
    for (int i = 0; i < sampleCount; i++) {
      sum += leaveIntervalsMillis[i];
    }
    return sum / sampleCount;
  }

  /**
   * Appends a departure interval to the ring buffer, evicting the oldest sample when full.
   */
  private void pushInterval(long millis) {
    leaveIntervalsMillis[writeIndex] = millis;
    writeIndex = (writeIndex + 1) % WINDOW_SIZE;
    if (sampleCount < WINDOW_SIZE) {
      sampleCount++;
    }
  }

  private static Duration sendDelayEta(int positions, long sendDelayMillis) {
    return Duration.ofMillis(positions * sendDelayMillis);
  }
}
