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

package com.velocitypowered.proxy.connection.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PlayerIdentityLockTest {

  @Test
  void canceledWaiterLeavesQueueAndNextIsGranted() {
    PlayerIdentityLock lock = new PlayerIdentityLock();
    UUID uuid = UUID.randomUUID();
    PlayerIdentityLock.LockHandle held = lock.acquire(uuid, "a").join();
    CompletableFuture<PlayerIdentityLock.LockHandle> first = lock.acquire(uuid, "a");
    CompletableFuture<PlayerIdentityLock.LockHandle> second = lock.acquire(uuid, "a");

    first.cancel(false);
    assertFalse(second.isDone());
    held.release();
    assertTrue(second.isDone() && !second.isCompletedExceptionally(), "next waiter granted");
    assertFalse(isFree(lock, uuid), "second holds it");
    second.join().release();
    assertTrue(isFree(lock, uuid), "free again, canceled waiter never held it");
  }

  @Test
  void canceledWaiterDoesNotBlockOtherIdentity() {
    PlayerIdentityLock lock = new PlayerIdentityLock();
    UUID uuid = UUID.randomUUID();
    PlayerIdentityLock.LockHandle held = lock.acquire(uuid, "a").join();
    CompletableFuture<PlayerIdentityLock.LockHandle> waiter = lock.acquire(uuid, "a");
    waiter.cancel(false);
    held.release();
    assertTrue(isFree(lock, uuid));
  }

  @Test
  void cancelRacingGrantNeverLeaksTheLock() throws Exception {
    for (int i = 0; i < 5000; i++) {
      PlayerIdentityLock lock = new PlayerIdentityLock();
      UUID uuid = UUID.randomUUID();
      PlayerIdentityLock.LockHandle held = lock.acquire(uuid, "a").join();
      CompletableFuture<PlayerIdentityLock.LockHandle> waiter = lock.acquire(uuid, "a");
      CountDownLatch go = new CountDownLatch(1);
      Thread canceler = new Thread(() -> {
        try {
          go.await();
        } catch (InterruptedException e) {
          return;
        }
        waiter.cancel(false);
      });
      canceler.start();
      go.countDown();
      held.release();
      canceler.join();
      if (waiter.isCancelled()) {
        assertTrue(isFree(lock, uuid), "run " + i + ": canceled waiter leaked the lock");
      } else {
        assertFalse(isFree(lock, uuid), "run " + i + ": granted waiter holds it");
        waiter.get(1, TimeUnit.SECONDS).release();
      }
    }
  }

  private static boolean isFree(PlayerIdentityLock lock, UUID uuid) {
    CompletableFuture<PlayerIdentityLock.LockHandle> probe = lock.acquire(uuid, "a");
    if (probe.isDone()) {
      probe.join().release();
      return true;
    }
    probe.cancel(false);
    return false;
  }
}
