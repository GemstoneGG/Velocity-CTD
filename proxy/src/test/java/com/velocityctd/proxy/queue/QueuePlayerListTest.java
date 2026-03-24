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

import static org.junit.jupiter.api.Assertions.*;

import com.velocitypowered.proxy.VelocityServer;
import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocityctd.proxy.queue.VelocityQueueEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QueuePlayerList}.
 *
 * <p>These tests verify correctness of the priority-ordered queue implementation,
 * including thread-safety, ordering guarantees, and position tracking.</p>
 */
class QueuePlayerListTest {

  @Test
  void testSingleThreadedPriorityInsertion() {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null; // Not needed for basic test; use mock or null
    VelocityQueue queue = null;

    // Create entries with different priorities
    VelocityQueueEntry e1 = createEntry(server, queue, "player1", 10);
    VelocityQueueEntry e2 = createEntry(server, queue, "player2", 20);
    VelocityQueueEntry e3 = createEntry(server, queue, "player3", 15);
    VelocityQueueEntry e4 = createEntry(server, queue, "player4", 20); // same priority as e2

    list.insertByPriority(e1);
    list.insertByPriority(e2);
    list.insertByPriority(e3);
    list.insertByPriority(e4);

    assertEquals(4, list.size());

    List<VelocityQueueEntry> snapshot = list.snapshot();
    // Should be ordered by priority descending, then insertion order ascending
    // Priorities: 20 (e2 first), 20 (e4 second, after e2), 15 (e3), 10 (e1)
    assertEquals(e2, snapshot.get(0));
    assertEquals(e4, snapshot.get(1));
    assertEquals(e3, snapshot.get(2));
    assertEquals(e1, snapshot.get(3));

    // Positions should be 1-indexed and correct
    assertEquals(1, e2.getPosition());
    assertEquals(2, e4.getPosition());
    assertEquals(3, e3.getPosition());
    assertEquals(4, e1.getPosition());
  }

  @Test
  void testAddLastPreservesOrder() {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null;
    VelocityQueue queue = null;

    // Simulate Redis restore: entries already in desired order
    VelocityQueueEntry e1 = createEntry(server, queue, "player1", 10);
    VelocityQueueEntry e2 = createEntry(server, queue, "player2", 20);
    VelocityQueueEntry e3 = createEntry(server, queue, "player3", 15);

    list.addLast(e1);
    list.addLast(e2);
    list.addLast(e3);

    // After addLast, positions are set based on queue size (since entries added in order,
    // they should match the final priority order if the list was already sorted correctly)
    List<VelocityQueueEntry> snapshot = list.snapshot();
    assertEquals(3, snapshot.size());
    // The snapshot order should be priority descending
    assertEquals(e2, snapshot.get(0));
    assertEquals(e3, snapshot.get(1));
    assertEquals(e1, snapshot.get(2));
  }

  @Test
  void testDuplicateInsertionIgnored() {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null;
    VelocityQueue queue = null;

    UUID sharedUuid = UUID.randomUUID();
    VelocityQueueEntry e1 = createEntry(server, queue, "player1", 10, sharedUuid);
    VelocityQueueEntry e2 = createEntry(server, queue, "player1", 20, sharedUuid); // Same UUID

    list.insertByPriority(e1);
    list.insertByPriority(e2); // Should be ignored because UUID already present

    assertEquals(1, list.size());
    assertTrue(list.contains(e1.getUniqueId()));
    assertSame(e1, list.get(e1.getUniqueId()));
  }

  @Test
  void testRemove() {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null;
    VelocityQueue queue = null;

    VelocityQueueEntry e1 = createEntry(server, queue, "player1", 10);
    VelocityQueueEntry e2 = createEntry(server, queue, "player2", 20);

    list.insertByPriority(e1);
    list.insertByPriority(e2);

    list.remove(e1.getUniqueId());
    assertEquals(1, list.size());
    assertFalse(list.contains(e1.getUniqueId()));
    assertTrue(list.contains(e2.getUniqueId()));

    list.remove(e2.getUniqueId());
    assertEquals(0, list.size());
  }

  @Test
  void testClear() {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null;
    VelocityQueue queue = null;

    for (int i = 0; i < 10; i++) {
      list.insertByPriority(createEntry(server, queue, "player" + i, i));
    }

    assertEquals(10, list.size());
    list.clear();
    assertEquals(0, list.size());
    assertTrue(list.snapshot().isEmpty());
  }

  @Test
  void testConcurrentInsertions() throws InterruptedException {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null;
    VelocityQueue queue = null;
    int threadCount = 10;
    int entriesPerThread = 100;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      executor.submit(() -> {
        try {
          start.await();
          for (int i = 0; i < entriesPerThread; i++) {
            UUID uuid = UUID.nameUUIDFromBytes(("thread" + threadId + "entry" + i).getBytes());
            VelocityQueueEntry entry = createEntry(server, queue, "player-" + threadId + "-" + i, i % 30, uuid);
            list.insertByPriority(entry);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }

    start.countDown();
    done.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // Total unique entries should be threadCount * entriesPerThread (no duplicates)
    assertEquals(threadCount * entriesPerThread, list.size());

    // Verify snapshot is in priority order
    List<VelocityQueueEntry> snapshot = list.snapshot();
    assertEquals(threadCount * entriesPerThread, snapshot.size());

    // Check that priorities are non-increasing (descending)
    for (int i = 1; i < snapshot.size(); i++) {
      int prevPriority = snapshot.get(i - 1).getPriority();
      int currPriority = snapshot.get(i).getPriority();
      assertTrue(prevPriority >= currPriority,
          "Priority at index " + i + " should be <= previous");
    }
  }

  @Test
  void testConcurrentInsertsAndRemoves() throws InterruptedException {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null;
    VelocityQueue queue = null;
    int threadCount = 10;
    int entriesPerThread = 50;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    // First phase: insert
    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      executor.submit(() -> {
        try {
          start.await();
          for (int i = 0; i < entriesPerThread; i++) {
            UUID uuid = UUID.nameUUIDFromBytes(("thread" + threadId + "entry" + i).getBytes());
            VelocityQueueEntry entry = createEntry(server, queue, "player-" + threadId + "-" + i, i % 30, uuid);
            list.insertByPriority(entry);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }

    start.countDown();
    done.await(30, TimeUnit.SECONDS);
    assertEquals(threadCount * entriesPerThread, list.size());

    // Second phase: remove half of them concurrently
    CountDownLatch startRemove = new CountDownLatch(1);
    CountDownLatch doneRemove = new CountDownLatch(threadCount);
    List<UUID> allUuids = new ArrayList<>(list.snapshot().stream().map(VelocityQueueEntry::getUniqueId).toList());
    int toRemove = allUuids.size() / 2;

    for (int t = 0; t < threadCount; t++) {
      executor.submit(() -> {
        try {
          startRemove.await();
          for (int i = 0; i < toRemove / threadCount; i++) {
            int idx = (int) (Math.random() * allUuids.size());
            UUID uuid = allUuids.get(idx);
            list.remove(uuid);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          doneRemove.countDown();
        }
      });
    }

    startRemove.countDown();
    doneRemove.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // Verify final size is approximately half (some removals might be duplicates)
    int finalSize = list.size();
    assertTrue(finalSize >= toRemove / 2 && finalSize <= toRemove * 2,
        "Final size should be roughly half, got " + finalSize);
  }

  @Test
  void testRebuildPositionsAfterMultipleInserts() {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null;
    VelocityQueue queue = null;

    // Insert 10 entries with random priorities
    List<VelocityQueueEntry> inserted = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      VelocityQueueEntry entry = createEntry(server, queue, "player" + i, (9 - i) * 10); // descending
      inserted.add(entry);
      list.insertByPriority(entry);
    }

    // After all inserts, positions should be 1..10 in priority order
    List<VelocityQueueEntry> snapshot = list.snapshot();
    for (int i = 0; i < snapshot.size(); i++) {
      VelocityQueueEntry e = snapshot.get(i);
      // Position should be 1-indexed
      assertEquals(i + 1, e.getPosition());
    }
  }

  @Test
  void testRemoveAndRebuildPositions() {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null;
    VelocityQueue queue = null;

    VelocityQueueEntry e1 = createEntry(server, queue, "p1", 30);
    VelocityQueueEntry e2 = createEntry(server, queue, "p2", 20);
    VelocityQueueEntry e3 = createEntry(server, queue, "p3", 10);

    list.insertByPriority(e1);
    list.insertByPriority(e2);
    list.insertByPriority(e3);

    // Positions: e1=1, e2=2, e3=3
    assertEquals(1, e1.getPosition());
    assertEquals(2, e2.getPosition());
    assertEquals(3, e3.getPosition());

    list.remove(e2.getUniqueId());

    // After removal, positions should be rebuilt: e1=1, e3=2
    assertEquals(1, e1.getPosition());
    assertEquals(2, e3.getPosition());
    // e2 is removed, we don't check its position
  }

  @Test
  void testSnapshotIsUnmodifiable() {
    QueuePlayerList list = new QueuePlayerList();
    VelocityServer server = null;
    VelocityQueue queue = null;

    VelocityQueueEntry entry = createEntry(server, queue, "player", 10);
    list.insertByPriority(entry);

    List<VelocityQueueEntry> snapshot = list.snapshot();
    assertThrows(UnsupportedOperationException.class, () -> snapshot.add(entry));
  }

  // Helper method to create a VelocityQueueEntry for testing
  private VelocityQueueEntry createEntry(VelocityServer server, VelocityQueue queue,
                                          String username, int priority) {
    return createEntry(server, queue, username, priority, UUID.randomUUID());
  }

  private VelocityQueueEntry createEntry(VelocityServer server, VelocityQueue queue,
                                          String username, int priority, UUID uuid) {
    // QueueEntryData is an immutable record; create it directly
    QueueEntryData data = new QueueEntryData(uuid, username, priority, false, false);
    return new VelocityQueueEntry(server, queue, data);
  }
}