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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Remembers, for a limited time, the server each player was on when their connection was lost, so
 * a player who reconnects soon after is sent back to that server instead of the default initial
 * one. Entries live on this proxy only and are used once.
 */
public final class LastServerTracker {

  private record Entry(String serverName, long expiresAtNanos) {

    boolean expired(long nowNanos) {
      return expiresAtNanos - nowNanos <= 0;
    }
  }

  private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
  private final LongSupplier nanoTime;

  public LastServerTracker() {
    this(System::nanoTime);
  }

  LastServerTracker(LongSupplier nanoTime) {
    this.nanoTime = nanoTime;
  }

  /**
   * Remembers the server a player was on when their connection was lost.
   *
   * @param player       the player's unique id
   * @param serverName   the name of the server they were on
   * @param windowMillis how long to keep it, in milliseconds; nothing is remembered if not positive
   */
  public void remember(UUID player, String serverName, long windowMillis) {
    if (windowMillis <= 0) {
      return;
    }

    long now = nanoTime.getAsLong();
    entries.values().removeIf(entry -> entry.expired(now));
    entries.put(player, new Entry(serverName, now + TimeUnit.MILLISECONDS.toNanos(windowMillis)));
  }

  /**
   * Returns, and forgets, the server a player was on when their connection was lost, if that was
   * recent enough.
   *
   * @param player the player's unique id
   * @return the server name, or empty if none is remembered or it has expired
   */
  public Optional<String> consume(UUID player) {
    Entry entry = entries.remove(player);
    if (entry == null || entry.expired(nanoTime.getAsLong())) {
      return Optional.empty();
    }
    return Optional.of(entry.serverName());
  }

  int size() {
    return entries.size();
  }
}
