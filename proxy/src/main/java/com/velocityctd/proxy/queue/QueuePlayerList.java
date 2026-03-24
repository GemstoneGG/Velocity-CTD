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

package com.velocityctd.proxy.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Encapsulates the ordered player queue and its UUID lookup index for a {@link VelocityQueue}.
 *
 * <p>Uses a {@link ConcurrentSkipListSet} for O(log n) priority-ordered insertion and
 * O(n) ordered iteration. Combined with a {@link ConcurrentHashMap} for O(1) UUID lookups,
 * this implementation provides significantly better performance and thread-safety compared
 * to the previous synchronized deque approach.</p>
 */
public final class QueuePlayerList {

  /**
   * Comparator for priority ordering: higher priority first, and within same priority,
   * earlier insertion (FIFO) comes first.
   */
  private static final Comparator<VelocityQueueEntry> PRIORITY_COMPARATOR =
      Comparator.comparingInt(VelocityQueueEntry::getPriority).reversed()
                .thenComparingLong(VelocityQueueEntry::getInsertionOrder);

  /**
   * The sorted set storing entries in priority order.
   * ConcurrentSkipListSet maintains natural order (by comparator) and provides
   * O(log n) insertion with O(n) ordered iteration. It is thread-safe.
   */
  private final ConcurrentSkipListSet<VelocityQueueEntry> queue;

  /**
   * Index for O(1) UUID lookup.
   */
  private final ConcurrentHashMap<UUID, VelocityQueueEntry> index;

  /**
   * Counter for assigning insertion order (monotonic increasing).
   * Used for FIFO tie-breaking when priorities are equal.
   */
  private final AtomicLong insertionCounter = new AtomicLong(0);

  public QueuePlayerList() {
    this.queue = new ConcurrentSkipListSet<>(PRIORITY_COMPARATOR);
    this.index = new ConcurrentHashMap<>();
  }

  /**
   * Inserts the entry in descending priority order, preserving FIFO within the same priority tier.
   * Silently ignores the call if an entry with the same UUID is already present.
   */
  public void insertByPriority(final VelocityQueueEntry entry) {
    // Use computeIfAbsent to atomically check and insert, preventing duplicates
    VelocityQueueEntry existing = index.computeIfAbsent(entry.getUniqueId(), k -> {
      // This lambda runs only if the UUID is not already present
      entry.setInsertionOrder(insertionCounter.getAndIncrement());
      queue.add(entry);
      return entry;
    });

    // If the existing entry is not the one we just attempted to insert,
    // it means a duplicate was detected and we should do nothing further.
    if (existing != entry) {
      return;
    }

    // Rebuild positions after successful insertion
    rebuildPositions();
  }

  /**
   * Appends the entry to the tail of the queue without priority sorting.
   * Used when restoring entries from a Redis depot snapshot, where ordering
   * is already correct. To preserve the restored order, we assign insertion
   * order sequentially and add to queue; priority sorting ensures correct placement
   * but we want FIFO for equal priorities.
   */
  public void addLast(final VelocityQueueEntry entry) {
    entry.setInsertionOrder(insertionCounter.getAndIncrement());
    queue.add(entry);
    index.put(entry.getUniqueId(), entry);
    entry.setPosition(queue.size());
  }

  /**
   * Removes the entry with the given UUID, if present.
   */
  public void remove(final UUID uniqueId) {
    VelocityQueueEntry removed = index.remove(uniqueId);
    if (removed != null) {
      queue.remove(removed);
      // Rebuild positions to fill gap - O(n) but acceptable for removal (rare vs insert)
      rebuildPositions();
    }
  }

  /**
   * Removes all entries.
   */
  public void clear() {
    queue.clear();
    index.clear();
  }

  /**
   * Returns {@code true} if an entry with the given UUID is present.
   */
  public boolean contains(final UUID uniqueId) {
    return index.containsKey(uniqueId);
  }

  /**
   * Returns the entry for the given UUID, or {@code null} if not present.
   */
  public @Nullable VelocityQueueEntry get(final UUID uniqueId) {
    return index.get(uniqueId);
  }

  /**
   * Returns the number of entries currently in the list.
   */
  public int size() {
    return index.size();
  }

  /**
   * Returns an unmodifiable ordered snapshot of all entries.
   * The snapshot is sorted by priority (descending) and insertion order (ascending).
   * ConcurrentSkipListSet iteration returns elements in natural order, so no explicit sort needed.
   */
  public List<VelocityQueueEntry> snapshot() {
    return List.copyOf(queue);
  }

  /**
   * Updates the position field for all entries based on current priority order.
   * This is O(n) and is necessary only when the queue structure changes.
   */
  private void rebuildPositions() {
    int pos = 1;
    // Use snapshot to get sorted order without holding any lock on the queue
    for (final VelocityQueueEntry entry : snapshot()) {
      entry.setPosition(pos++);
    }
  }
}