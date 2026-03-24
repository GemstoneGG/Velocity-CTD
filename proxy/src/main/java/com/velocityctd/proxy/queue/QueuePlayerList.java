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
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Encapsulates the ordered player queue and its UUID lookup index for a {@link VelocityQueue}.
 *
 * <p>Uses a {@link PriorityBlockingQueue} for efficient priority-ordered insertion
 * combined with a {@link ConcurrentHashMap} for O(1) UUID lookups.</p>
 */
public final class QueuePlayerList {

  /**
   * Sorts entries by priority (higher first), then by insertion order (lower first) for FIFO.
   */
  private static final Comparator<VelocityQueueEntry> PRIORITY_COMPARATOR =
      Comparator.comparingInt(VelocityQueueEntry::getPriority).reversed()
                .thenComparingLong(VelocityQueueEntry::getInsertionOrder);

  private final PriorityBlockingQueue<VelocityQueueEntry> queue;
  private final ConcurrentHashMap<UUID, VelocityQueueEntry> index = new ConcurrentHashMap<>();
  private final AtomicLong insertionCounter = new AtomicLong(0);

  public QueuePlayerList() {
    this.queue = new PriorityBlockingQueue<>(1000, PRIORITY_COMPARATOR);
  }

  /**
   * Inserts the entry in descending priority order, preserving FIFO within the same priority tier.
   * Silently ignores the call if an entry with the same UUID is already present.
   */
  public void insertByPriority(final VelocityQueueEntry entry) {
    if (index.containsKey(entry.getUniqueId())) {
      return;
    }

    entry.setInsertionOrder(insertionCounter.getAndIncrement());
    queue.put(entry);
    index.put(entry.getUniqueId(), entry);
    rebuildPositions();
  }

  /**
   * Appends the entry to the queue. Used when restoring entries from a Redis depot snapshot,
   * where ordering is already correct.
   */
  public void addLast(final VelocityQueueEntry entry) {
    entry.setInsertionOrder(insertionCounter.getAndIncrement());
    queue.put(entry);
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
   * Returns an unmodifiable ordered snapshot of all entries in priority order.
   */
  public List<VelocityQueueEntry> snapshot() {
    List<VelocityQueueEntry> list = new ArrayList<>(queue);
    list.sort(PRIORITY_COMPARATOR);
    return List.copyOf(list);
  }

  private void rebuildPositions() {
    int pos = 1;
    for (VelocityQueueEntry entry : snapshot()) {
      entry.setPosition(pos++);
    }
  }
}
