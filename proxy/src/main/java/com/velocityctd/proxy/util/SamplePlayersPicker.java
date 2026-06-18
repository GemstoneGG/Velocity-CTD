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

package com.velocityctd.proxy.util;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingLong;

import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocitypowered.proxy.VelocityServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SamplePlayersPicker {

  private final Supplier<Collection<VelocityClusterPlayer>> poolSupplier;

  private @Nullable List<VelocityClusterPlayer> pool;

  public SamplePlayersPicker(Supplier<Collection<VelocityClusterPlayer>> poolSupplier) {
    this.poolSupplier = poolSupplier;
  }

  public static SamplePlayersPicker create(VelocityServer server) {
    return new SamplePlayersPicker(server.getClusterPlayerService()::getAllPlayers);
  }

  public List<VelocityClusterPlayer> samplePlayers(int sampleSize, @NonNull Ordering ordering) {
    if (pool == null) {
      pool = new ArrayList<>(poolSupplier.get());
    }

    if (sampleSize < 0) {
      sampleSize = 0;
    }

    if (ordering == Ordering.RANDOM) {
      return pollRandom(pool, sampleSize);
    } else {
      return pollOrdered(pool, sampleSize, ordering.comparator());
    }
  }

  private static <E> List<E> pollRandom(List<E> pool, int sampleSize) {
    if (sampleSize > pool.size()) {
      sampleSize = pool.size();
    }

    if (sampleSize <= 0) {
      return new ArrayList<>(0);
    }

    List<E> result = new ArrayList<>(sampleSize);

    ThreadLocalRandom rand = ThreadLocalRandom.current();
    for (int i = 0; i < sampleSize; i++) {
      int index = rand.nextInt(pool.size());
      result.add(pool.get(index));

      int last = pool.size() - 1;
      pool.set(index, pool.get(last));
      pool.remove(last);
    }

    return result;
  }

  private static <E> List<E> pollOrdered(List<E> pool, int sampleSize, Comparator<E> comparator) {
    if (sampleSize > pool.size()) {
      sampleSize = pool.size();
    }

    if (sampleSize <= 0) {
      return new ArrayList<>(0);
    }

    PriorityQueue<E> heap = new PriorityQueue<>(sampleSize, comparator.reversed());
    for (E entry : pool) {
      if (heap.size() < sampleSize) {
        heap.add(entry);
      } else if (comparator.compare(entry, heap.peek()) < 0) {
        heap.poll(); // evict current worst
        heap.add(entry);
      }
    }

    // Heap iteration is NOT sorted - materialize then sort the survivors
    List<E> result = new ArrayList<>(heap);
    result.sort(comparator);

    // Remove chosen instances from the pool. Identity, not equals()
    Set<E> chosen = Collections.newSetFromMap(new IdentityHashMap<>());
    chosen.addAll(result);
    pool.removeIf(chosen::contains); // O(n) but acceptable - this entire method is O(n log sampleSize)

    return result;
  }

  public enum Ordering {

    RANDOM(null),
    ALPHABETICAL(comparing(Ordering::lowerCaseUsername)),
    ALPHABETICAL_REVERSED(comparing(Ordering::lowerCaseUsername).reversed()),
    LAST_JOINED(comparingLong(VelocityClusterPlayer::getJoinedAt).reversed()),
    FIRST_JOINED(comparingLong(VelocityClusterPlayer::getJoinedAt));

    private final @Nullable Comparator<VelocityClusterPlayer> comparator;

    Ordering(Comparator<VelocityClusterPlayer> comparator) {
      this.comparator = comparator;
    }

    private @NonNull Comparator<VelocityClusterPlayer> comparator() {
      if (comparator == null) {
        throw new IllegalStateException("This Ordering is not comparable.");
      }
      return comparator;
    }

    private static String lowerCaseUsername(VelocityClusterPlayer player) {
      return player.getUsername().toLowerCase(Locale.ROOT);
    }
  }
}
