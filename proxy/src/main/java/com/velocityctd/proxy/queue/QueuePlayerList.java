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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Encapsulates the ordered player list and its UUID lookup index for a {@link VelocityQueue}.
 */
public final class QueuePlayerList<E extends VelocityQueueEntry> {

  /**
   * The ordered list of entries.
   *
   * <p>Kept strictly in lockstep with {@link #index}: every entry present in {@code players} is
   * also present in {@code index} under its UUID key, and vice versa. All mutations of either
   * collection occur inside a {@code synchronized(this)} block, so an entry obtained from
   * {@code index} can be located in {@code players} via {@link List#indexOf(Object)} (using
   * reference equality, since {@link VelocityQueueEntry} does not override {@code equals}).</p>
   */
  private final List<E> players = new ArrayList<>();

  /**
   * UUID-keyed lookup for {@link #players}. See the contract documented on that field: the two
   * collections are kept in lockstep under the instance monitor.
   */
  private final ConcurrentHashMap<UUID, E> index = new ConcurrentHashMap<>();

  /**
   * Inserts the entry in descending priority order, preserving FIFO within the same priority tier.
   * Silently ignores the call if an entry with the same UUID is already present.
   */
  public synchronized void insertByPriority(E entry) {
    if (index.containsKey(entry.getUniqueId())) {
      return;
    }

    int size = players.size();

    if (size == 0 || players.get(size - 1).getPriority() >= entry.getPriority()) {
      players.add(entry);
      index.put(entry.getUniqueId(), entry);
      entry.setPosition(size + 1);
      return;
    }

    int target = size;
    for (int i = 0; i < size; i++) {
      if (players.get(i).getPriority() < entry.getPriority()) {
        target = i;
        break;
      }
    }

    players.add(target, entry);
    index.put(entry.getUniqueId(), entry);
    renumberFrom(target);
  }

  /**
   * Appends the entry to the tail of the list without priority sorting.
   * Used when restoring entries from a Redis depot snapshot, where ordering
   * is already correct.
   */
  public synchronized void addLast(E entry) {
    players.add(entry);
    index.put(entry.getUniqueId(), entry);
    entry.setPosition(players.size());
  }

  /**
   * Removes the entry with the given UUID, if present.
   */
  public synchronized void remove(UUID uniqueId) {
    E removed = index.remove(uniqueId);
    if (removed == null) {
      return;
    }

    int idx = players.indexOf(removed);
    players.remove(idx);
    renumberFrom(idx);
  }

  /**
   * Removes all entries.
   */
  public synchronized void clear() {
    players.clear();
    index.clear();
  }

  /**
   * Stable-sorts the entries by descending rank and reassigns all positions. Each entry's
   * rank is computed once before sorting, so concurrent mutation of the fields a rank is
   * derived from cannot destabilize the sort.
   */
  public synchronized void sortByRankDescending(ToIntFunction<? super E> rank) {
    if (players.size() < 2) {
      return;
    }

    Map<E, Integer> ranks = new IdentityHashMap<>(players.size());
    for (E entry : players) {
      ranks.put(entry, rank.applyAsInt(entry));
    }

    players.sort(Comparator.<E>comparingInt(ranks::get).reversed());
    renumberFrom(0);
  }

  /**
   * Returns {@code true} if an entry with the given UUID is present.
   */
  public boolean contains(UUID uniqueId) {
    return index.containsKey(uniqueId);
  }

  /**
   * Returns the entry for the given UUID, or {@code null} if not present.
   */
  public @Nullable E get(UUID uniqueId) {
    return index.get(uniqueId);
  }

  /**
   * Returns the number of entries currently in the list.
   */
  public int size() {
    return index.size();
  }

  /**
   * Returns an unmodifiable, point-in-time ordered snapshot of all entries.
   */
  public synchronized List<E> snapshot() {
    return List.copyOf(players);
  }

  /**
   * Reassigns 1-based positions to every entry from {@code fromIndex} onward.
   * Entries before {@code fromIndex} keep their existing positions.
   */
  private void renumberFrom(int fromIndex) {
    for (int i = fromIndex; i < players.size(); i++) {
      players.get(i).setPosition(i + 1);
    }
  }
}
