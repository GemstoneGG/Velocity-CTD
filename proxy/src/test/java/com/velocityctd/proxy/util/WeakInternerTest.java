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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * The interner hands out one instance per key and lets it go once nobody else holds it.
 */
class WeakInternerTest {

  @Test
  void sameKeyGivesTheFirstInstance() {
    WeakInterner<String, Object> interner = new WeakInterner<>();
    Object first = new Object();

    assertSame(first, interner.intern("a", first));
    assertSame(first, interner.intern("a", new Object()));
    assertSame(first, interner.intern(new String("a"), ignored -> new Object()));
  }

  @Test
  void differentKeysGiveDifferentInstances() {
    WeakInterner<String, Object> interner = new WeakInterner<>();

    Object first = interner.intern("a", new Object());
    Object second = interner.intern("b", new Object());

    assertNotSame(first, second);
    assertEquals(2, interner.size());
  }

  @Test
  void collectedValuesAreForgotten() throws InterruptedException {
    WeakInterner<String, Object> interner = new WeakInterner<>();
    WeakReference<Object> reference = new WeakReference<>(interner.intern("a", new Object()));

    awaitCollection(reference);

    assertNull(reference.get());
    assertEquals(0, interner.size());

    Object replacement = new Object();
    assertSame(replacement, interner.intern("a", replacement));
  }

  @Test
  void concurrentCallersShareOneInstance() throws Exception {
    WeakInterner<String, Object> interner = new WeakInterner<>();
    int threads = 8;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);

    try {
      List<Future<Object>> results = new ArrayList<>();
      for (int index = 0; index < threads; index++) {
        results.add(executor.submit(() -> {
          start.await();
          return interner.intern("shared", ignored -> new Object());
        }));
      }

      start.countDown();

      Set<Object> distinct = ConcurrentHashMap.newKeySet();
      for (Future<Object> result : results) {
        distinct.add(result.get());
      }

      assertEquals(1, distinct.size());
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Asks for garbage collection until the reference clears, giving up after a few seconds.
   */
  private static void awaitCollection(WeakReference<?> reference) throws InterruptedException {
    for (int attempt = 0; attempt < 100 && reference.get() != null; attempt++) {
      System.gc();
      Thread.sleep(20);
    }
  }
}
