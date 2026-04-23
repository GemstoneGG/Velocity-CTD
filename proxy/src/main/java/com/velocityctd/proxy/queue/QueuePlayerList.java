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
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Encapsulates the ordered player deque and its UUID lookup index for a {@link VelocityQueue}.
 */
public final class QueuePlayerList {

  private final ConcurrentLinkedDeque<VelocityQueueEntry> players = new ConcurrentLinkedDeque<>();
  private final ConcurrentHashMap<UUID, VelocityQueueEntry> index = new ConcurrentHashMap<>();

  /**
   * Inserts the entry in descending priority order, preserving FIFO within the same priority tier.
   * Silently ignores the call if an entry with the same UUID is already present.
   */
  public synchronized void insertByPriority(VelocityQueueEntry entry) {
    if (index.containsKey(entry.getUniqueId())) {
      return;
    }

    Iterator<VelocityQueueEntry> it = players.iterator();
    int position = 0;
    boolean inserted = false;

    while (it.hasNext()) {
      if (it.next().getPriority() < entry.getPriority()) {
        insertAt(entry, position);
        inserted = true;
        break;
      }
      position++;
    }

    if (!inserted) {
      players.addLast(entry);
    }

    index.put(entry.getUniqueId(), entry);
    rebuildPositions();
  }

  /**
   * Appends the entry to the tail of the deque without priority sorting.
   * Used when restoring entries from a Redis depot snapshot, where ordering
   * is already correct.
   */
  public synchronized void addLast(VelocityQueueEntry entry) {
    players.addLast(entry);
    index.put(entry.getUniqueId(), entry);
    entry.setPosition(players.size());
  }

  /**
   * Removes the entry with the given UUID, if present.
   */
  public synchronized void remove(UUID uniqueId) {
    players.removeIf(p -> p.getUniqueId().equals(uniqueId));
    index.remove(uniqueId);
    rebuildPositions();
  }

  /**
   * Removes all entries.
   */
  public synchronized void clear() {
    players.clear();
    index.clear();
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
  public @Nullable VelocityQueueEntry get(UUID uniqueId) {
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
   */
  public List<VelocityQueueEntry> snapshot() {
    return List.copyOf(players);
  }

  private void insertAt(VelocityQueueEntry entry, int position) {
    List<VelocityQueueEntry> tempList = new ArrayList<>(players);
    tempList.add(position, entry);
    players.clear();
    players.addAll(tempList);
  }

  private void rebuildPositions() {
    int pos = 1;
    for (VelocityQueueEntry entry : players) {
      entry.setPosition(pos++);
    }
  }
}
