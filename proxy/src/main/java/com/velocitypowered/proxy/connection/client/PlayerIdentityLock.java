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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * An asynchronous, non-reentrant lock keyed by the (uuid, name) tuple of a player.
 *
 * <p>A lock is considered held for a given (uuid, name) pair if either {@code uuid} is currently
 * locked, OR {@code name} is currently locked. This means an attempt to register a player whose
 * uuid matches one already in flight, or whose name matches one already in flight, must wait.
 *
 * <p>The lock is non-blocking: {@link #acquire(UUID, String)} returns a {@link CompletableFuture}
 * that completes when the lock is granted. Waiters are stored in a FIFO queue, but the lock is
 * not strictly FIFO across all identities: a later waiter whose required (uuid, name) pair is
 * free may be granted ahead of an earlier waiter who is still blocked. Per-identity ordering is
 * preserved (two waiters for the same identity are granted in FIFO order).
 */
public final class PlayerIdentityLock {

  private final Object monitor = new Object();
  private final Set<UUID> lockedUuids = new HashSet<>();
  private final Set<String> lockedNames = new HashSet<>();
  private final Deque<Waiter> waiters = new ArrayDeque<>();

  /**
   * Acquires the identity lock for the given (uuid, name) pair.
   *
   * @param uuid the player's uuid
   * @param name the player's lowercased name
   * @return a future that resolves to the held lock handle once acquired
   */
  public @NonNull CompletableFuture<LockHandle> acquire(@NonNull UUID uuid, @NonNull String name) {
    synchronized (monitor) {
      if (!isLocked(uuid, name)) {
        lockedUuids.add(uuid);
        lockedNames.add(name);
        return CompletableFuture.completedFuture(new LockHandle(uuid, name));
      }

      CompletableFuture<LockHandle> future = new CompletableFuture<>();
      waiters.add(new Waiter(uuid, name, future));
      return future;
    }
  }

  private boolean isLocked(UUID uuid, String name) {
    return lockedUuids.contains(uuid) || lockedNames.contains(name);
  }

  private void release(UUID uuid, String name) {
    List<Waiter> granted = null;
    synchronized (monitor) {
      lockedUuids.remove(uuid);
      lockedNames.remove(name);

      Iterator<Waiter> it = waiters.iterator();
      while (it.hasNext()) {
        Waiter w = it.next();
        if (!isLocked(w.uuid, w.name)) {
          lockedUuids.add(w.uuid);
          lockedNames.add(w.name);
          it.remove();
          if (granted == null) {
            granted = new ArrayList<>(2);
          }
          granted.add(w);
        }
      }
    }
    if (granted != null) {
      for (Waiter w : granted) {
        w.future.complete(new LockHandle(w.uuid, w.name));
      }
    }
  }

  /**
   * A held lock for a (uuid, name) pair. Release the lock exactly once via {@link #release()};
   * subsequent calls are no-ops.
   */
  public final class LockHandle {

    private final UUID uuid;
    private final String name;
    private final AtomicBoolean released = new AtomicBoolean(false);

    private LockHandle(UUID uuid, String name) {
      this.uuid = uuid;
      this.name = name;
    }

    /**
     * Releases the lock, granting it to the next eligible waiter (if any). Safe to call multiple
     * times; only the first call has an effect.
     */
    public void release() {
      if (released.compareAndSet(false, true)) {
        PlayerIdentityLock.this.release(uuid, name);
      }
    }

    public boolean isReleased() {
      return released.get();
    }
  }

  private record Waiter(UUID uuid, String name, CompletableFuture<LockHandle> future) {
  }
}
