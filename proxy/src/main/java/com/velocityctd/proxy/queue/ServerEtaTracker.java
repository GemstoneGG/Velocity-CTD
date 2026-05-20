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
   * Updates the most recently observed backend player count and capacity. This is fed by the
   * periodic backend ping and feeds the {@code mustLeave} term of {@link #estimateEta(int, double)}
   * only - it no longer produces interval samples.
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
   * Estimates the wait, in seconds, before the player at the given position is transferred.
   *
   * @param position                 the 1-based queue position
   * @param fallbackSendDelaySeconds the send-delay fallback in seconds
   * @return                         the estimated wait in seconds
   */
  public synchronized int estimateEta(int position, double fallbackSendDelaySeconds) {
    if (lastOnline == UNKNOWN || lastMax <= 0) {
      return sendDelayEta(position, fallbackSendDelaySeconds);
    }

    int availableNow = Math.max(0, lastMax - lastOnline);
    int deficit = Math.max(0, lastOnline - lastMax);

    int sendDelaySteps = Math.min(position, availableNow);
    int leaveSteps = (position - sendDelaySteps) + deficit;

    if (leaveSteps == 0) {
      // The player fits within the current free spots; no leaves required.
      return sendDelayEta(sendDelaySteps, fallbackSendDelaySeconds);
    }

    double averageInterval = averageLeaveIntervalSeconds();
    if (averageInterval <= 0.0) {
      // No leave samples yet - degrade gracefully to a flat per-position send-delay estimate.
      return sendDelayEta(position, fallbackSendDelaySeconds);
    }

    double total = sendDelaySteps * fallbackSendDelaySeconds + leaveSteps * averageInterval;
    return (int) Math.clamp(Math.round(total), 0L, Integer.MAX_VALUE);
  }

  /**
   * Returns the flat per-position send-delay estimate used for the free-spot region and as
   * a degraded fallback when no leave samples are available.
   */
  private static int sendDelayEta(int positions, double sendDelaySeconds) {
    return (int) Math.max(0L, Math.round(sendDelaySeconds * positions));
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
   *
   * @return the recorded intervals in seconds, oldest first
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
   * live player count from its next ping, and the last-leave timestamp is cleared so the
   * first imported-state leave does not produce a bogus interval.
   *
   * @param samples the departure intervals to load, or {@code null} for none
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
