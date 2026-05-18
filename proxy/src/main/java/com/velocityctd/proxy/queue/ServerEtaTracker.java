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

/**
 * Tracks how quickly players leave a backend server and estimates how long a queued player
 * must wait before a slot frees up for them.
 */
public final class ServerEtaTracker {

  private static final int UNKNOWN = -1;
  private static final int WINDOW_SIZE = 20;

  private final double[] leaveIntervals = new double[WINDOW_SIZE];

  private int sampleCount;
  private int writeIndex;
  private int lastOnline = UNKNOWN;
  private int lastMax;

  private long lastLeaveMillis;

  /**
   * Records a backend player-count observation from a server ping. A drop in the online count
   * since the last observation is treated as one or more departures and folded into the
   * rolling average.
   */
  public synchronized void recordPing(int online, int max, long nowMillis) {
    this.lastMax = max;

    if (lastOnline != UNKNOWN && online < lastOnline) {
      int leaves = lastOnline - online;

      if (lastLeaveMillis != 0L) {
        double intervalSeconds = (nowMillis - lastLeaveMillis) / 1000.0;
        if (intervalSeconds > 0.0) {
          double perLeave = intervalSeconds / leaves;
          int pushes = Math.min(leaves, WINDOW_SIZE);
          for (int i = 0; i < pushes; i++) {
            pushInterval(perLeave);
          }
        }
      }

      lastLeaveMillis = nowMillis;
    }

    this.lastOnline = online;
  }

  /**
   * Clears the online baseline so the next observation does not register a bogus mass
   * departure, for example after the backend becomes unreachable. Recorded departure
   * intervals are kept as a prior.
   */
  public synchronized void reset() {
    this.lastOnline = UNKNOWN;
    this.lastLeaveMillis = 0L;
  }

  /**
   * Estimates the wait, in seconds, before the player at the given position is transferred.
   * Falls back to the send-delay estimate when the backend already has room for the position
   * or no departures have been observed yet.
   */
  public synchronized int estimateEta(int position, double fallbackSendDelaySeconds) {
    int fallback = (int) Math.max(0L, Math.round(fallbackSendDelaySeconds * position));

    int mustLeave = playersThatMustLeave(position);
    if (mustLeave <= 0) {
      // The backend already has room for this position; the send cadence is the bound.
      return fallback;
    }

    double averageInterval = averageLeaveIntervalSeconds();
    if (averageInterval <= 0.0) {
      // No departure samples yet - fall back to the send-delay estimate.
      return fallback;
    }

    long eta = Math.round(mustLeave * averageInterval);
    return (int) Math.clamp(eta, 0L, Integer.MAX_VALUE);
  }

  /**
   * Returns how many players must leave before the player at the given position can be
   * transferred, or {@code 0} if this cannot be determined yet.
   */
  private int playersThatMustLeave(int position) {
    if (lastOnline == UNKNOWN || lastMax <= 0) {
      return 0;
    }
    return Math.max(0, (lastOnline - lastMax) + position);
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

  /**
   * Exports the recorded departure intervals, oldest first, so they can be persisted and
   * later restored via {@link #importSamples(double[])}.
   */
  public synchronized double[] exportSamples() {
    double[] out = new double[sampleCount];
    int start = (sampleCount < WINDOW_SIZE) ? 0 : writeIndex;
    for (int i = 0; i < sampleCount; i++) {
      out[i] = leaveIntervals[(start + i) % WINDOW_SIZE];
    }
    return out;
  }

  /**
   * Replaces the departure-interval window with a previously exported snapshot, keeping only
   * the most recent samples. The online baseline stays cleared so the tracker re-learns the
   * live player count from its next ping.
   */
  public synchronized void importSamples(double[] samples) {
    sampleCount = 0;
    writeIndex = 0;
    lastOnline = UNKNOWN;
    lastLeaveMillis = 0L;

    if (samples == null) {
      return;
    }

    int from = Math.max(0, samples.length - WINDOW_SIZE);
    for (int i = from; i < samples.length; i++) {
      if (samples[i] > 0.0) {
        pushInterval(samples[i]);
      }
    }
  }
}
