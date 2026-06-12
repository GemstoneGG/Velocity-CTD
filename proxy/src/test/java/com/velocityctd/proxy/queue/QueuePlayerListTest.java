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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.velocityctd.api.queue.QueueEntryData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueuePlayerListTest {

  private static VelocityQueueEntry entry(String username, int priority) {
    return new VelocityQueueEntry(null, null,
        new QueueEntryData(UUID.randomUUID(), username, priority, false, false));
  }

  private static List<String> usernames(QueuePlayerList<VelocityQueueEntry> list) {
    return list.snapshot().stream().map(VelocityQueueEntry::getUsername).toList();
  }

  @Test
  void sortsEntriesByDescendingRank() {
    QueuePlayerList<VelocityQueueEntry> list = new QueuePlayerList<>();
    list.insertByPriority(entry("high", 10));
    list.insertByPriority(entry("mid", 5));
    list.insertByPriority(entry("low", 0));

    // Rank inversely to the configured priority, as if "low" waited the longest.
    list.sortByRankDescending(e -> 10 - e.getPriority());

    assertEquals(List.of("low", "mid", "high"), usernames(list));
  }

  @Test
  void keepsInsertionOrderForEqualRanks() {
    QueuePlayerList<VelocityQueueEntry> list = new QueuePlayerList<>();
    list.insertByPriority(entry("first", 0));
    list.insertByPriority(entry("second", 0));
    list.insertByPriority(entry("third", 0));

    list.sortByRankDescending(e -> 7);

    assertEquals(List.of("first", "second", "third"), usernames(list));
  }

  @Test
  void reassignsPositionsAfterSorting() {
    QueuePlayerList<VelocityQueueEntry> list = new QueuePlayerList<>();
    VelocityQueueEntry high = entry("high", 10);
    VelocityQueueEntry low = entry("low", 0);
    list.insertByPriority(high);
    list.insertByPriority(low);

    list.sortByRankDescending(e -> -e.getPriority());

    assertEquals(1, low.getPosition());
    assertEquals(2, high.getPosition());
  }
}
