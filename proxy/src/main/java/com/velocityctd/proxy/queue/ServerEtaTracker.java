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

import com.velocitypowered.proxy.VelocityServer;
import org.jetbrains.annotations.NotNull;

/**
 * Tracks how quickly players leave a backend server and estimates how long a queued player
 * must wait before a slot frees up for them.
 */
public final class ServerEtaTracker {

  private static final int UNKNOWN = -1;
  private static final int WINDOW_SIZE = 20;

  private final VelocityServer server;
  private final double[] leaveIntervals = new double[WINDOW_SIZE];

  private int sampleCount;
  private int writeIndex;
  private int lastOnline = UNKNOWN;
  private int lastMax;

  private long lastLeaveMillis;

  /**
   * Creates a fresh tracker bound to the given proxy server. The proxy reference is used to
   * read the live {@code send-delay} setting from the queue configuration on each
   * {@link #calculateEta(int)} call, so configuration reloads take effect immediately.
   *
   * @param server the proxy server this tracker draws its send-delay from
   */
  public ServerEtaTracker(@NotNull VelocityServer server) {
    this.server = server;
  }

  /**
   * Updates the most recently observed backend player count and capacity. This is fed by the
   * periodic backend ping and feeds the {@code mustLeave} term of {@link #calculateEta(int)}
   * only - it does not produce interval samples.
   *
   * @param online the current online player count on the backend
   * @param max    the backend's maximum player capacity
   */
  public synchronized void recordPing(int online, int max) {
    this.lastOnline = online;
    this.lastMax = max;
  }

  /**
   * Records a single player departure from the backend at the given wall-clock timestamp.
   * The interval since the previous departure (if any) is appended to the rolling window.
   *
   * @param nowMillis the wall-clock timestamp of the departure in epoch milliseconds
   */
  public synchronized void recordLeave(long nowMillis) {
    if (lastLeaveMillis != 0L) {
      double intervalSeconds = (nowMillis - lastLeaveMillis) / 1000.0;
      if (intervalSeconds > 0.0) {
        pushInterval(intervalSeconds);
      }
    }

    lastLeaveMillis = nowMillis;
  }

  /**
   * Clears the online baseline and the last-leave timestamp so that the next observations do
   * not register against stale state - for example after the backend becomes unreachable.
   * Recorded departure intervals are kept as a prior.
   */
  public synchronized void reset() {
    this.lastOnline = UNKNOWN;
    this.lastLeaveMillis = 0L;
  }

  /**
   * Computes the estimated wait, in seconds, before the player at the given position is
   * transferred. The send-delay component is sourced from the proxy's live queue
   * configuration; callers truncate the returned value to whatever unit they need.
   *
   * @param position the 1-based queue position
   * @return         the estimated wait in seconds, never negative
   */
  public synchronized double calculateEta(int position) {
    double sendDelay = server.getConfiguration().getQueue().getSendDelay();

    if (lastOnline == UNKNOWN || lastMax <= 0) {
      return sendDelayEta(position, sendDelay);
    }

    int availableNow = Math.max(0, lastMax - lastOnline);
    int deficit = Math.max(0, lastOnline - lastMax);

    int sendDelaySteps = Math.min(position, availableNow);
    int leaveSteps = (position - sendDelaySteps) + deficit;

    if (leaveSteps == 0) {
      // The player fits within the current free spots; no leaves required.
      return sendDelayEta(sendDelaySteps, sendDelay);
    }

    double averageInterval = averageLeaveIntervalSeconds();
    if (averageInterval <= 0.0) {
      // No leave samples yet - degrade gracefully to a flat per-position send-delay estimate.
      return sendDelayEta(position, sendDelay);
    }

    double total = sendDelaySteps * sendDelay + leaveSteps * averageInterval;
    return Math.max(0.0, total);
  }

  /**
   * Returns the flat per-position send-delay estimate, used for the free-spot region and as a
   * degraded fallback when no leave samples are available.
   */
  private static double sendDelayEta(int positions, double sendDelaySeconds) {
    return Math.max(0.0, sendDelaySeconds * positions);
  }

  /**
   * Returns the average windowed departure interval in seconds, or {@code 0} if no samples
   * have been recorded.
   */
  private double averageLeaveIntervalSeconds() {
    if (sampleCount == 0) {
      return 0.0;
    }

    double sum = 0.0;
    for (int i = 0; i < sampleCount; i++) {
      sum += leaveIntervals[i];
    }
    return sum / sampleCount;
  }

  /**
   * Appends a departure interval to the ring buffer, evicting the oldest sample when full.
   */
  private void pushInterval(double seconds) {
    leaveIntervals[writeIndex] = seconds;
    writeIndex = (writeIndex + 1) % WINDOW_SIZE;
    if (sampleCount < WINDOW_SIZE) {
      sampleCount++;
    }
  }
}
