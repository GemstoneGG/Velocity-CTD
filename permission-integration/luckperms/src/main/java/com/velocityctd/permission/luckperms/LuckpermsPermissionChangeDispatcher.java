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

package com.velocityctd.permission.luckperms;

import com.velocityctd.api.permission.PermissionChangeListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Bridges LuckPerms' {@link UserDataRecalculateEvent} to {@link PermissionChangeListener}s.
 */
final class LuckpermsPermissionChangeDispatcher {

  private static final Logger LOGGER = LogManager.getLogger(LuckpermsPermissionChangeDispatcher.class);

  private static final long COOLDOWN_MILLIS = 100L;

  // Values are copy-on-write: a published Set is never mutated in place, so dispatch can iterate the
  // snapshot it reads from the map without locking. The common single-listener case avoids
  // allocating a backing Set by storing a `Collections.singleton()`.
  private final ConcurrentMap<UUID, Set<Registration>> registrations = new ConcurrentHashMap<>();

  LuckpermsPermissionChangeDispatcher(LuckPerms api) {
    //noinspection resource
    api.getEventBus().subscribe(UserDataRecalculateEvent.class, this::onRecalculate);
  }

  /**
   * Registers a listener for changes to the given subject's permissions.
   *
   * @param subjectId the subject (player) unique id
   * @param listener the listener to notify
   * @return a handle that unregisters the listener when closed
   */
  AutoCloseable register(UUID subjectId, PermissionChangeListener listener) {
    Registration registration = new Registration(subjectId, listener);
    registrations.compute(subjectId, (id, existing) -> {
      if (existing == null) {
        return Collections.singleton(registration);
      }
      Set<Registration> expanded = new HashSet<>(existing);
      expanded.add(registration);
      return expanded;
    });
    return registration;
  }

  private void onRecalculate(UserDataRecalculateEvent event) {
    Set<Registration> subjectRegistrations = registrations.get(event.getUser().getUniqueId());
    if (subjectRegistrations == null) {
      return;
    }

    for (Registration registration : subjectRegistrations) {
      registration.notifyChanged();
    }
  }

  private void unregister(Registration registration) {
    registrations.computeIfPresent(registration.subjectId, (id, existing) -> {
      if (existing.size() == 1) {
        return existing.contains(registration) ? null : existing;
      }
      Set<Registration> reduced = new HashSet<>(existing);
      reduced.remove(registration);
      return reduced;
    });
  }

  /**
   * A single listener registration. Debounces deliveries so that a burst of recalculations for one
   * subject results in a single notification, and unregisters itself when closed.
   */
  private final class Registration implements AutoCloseable {

    private final UUID subjectId;
    private final PermissionChangeListener listener;
    private @Nullable CompletableFuture<Void> pending;
    private boolean closed;

    Registration(UUID subjectId, PermissionChangeListener listener) {
      this.subjectId = subjectId;
      this.listener = listener;
    }

    synchronized void notifyChanged() {
      if (closed) {
        return;
      }

      if (pending != null) {
        pending.cancel(false);
      }

      CompletableFuture<Void> scheduled = CompletableFuture.runAsync(
          listener::onPermissionChange,
          CompletableFuture.delayedExecutor(COOLDOWN_MILLIS, TimeUnit.MILLISECONDS));
      scheduled.whenComplete((result, throwable) -> {
        if (throwable != null && !(throwable instanceof CancellationException)) {
          LOGGER.error("A permission change listener threw an exception.", throwable);
        }
      });
      pending = scheduled;
    }

    @Override
    public synchronized void close() {
      if (closed) {
        return;
      }
      closed = true;
      if (pending != null) {
        pending.cancel(false);
      }
      unregister(this);
    }
  }
}
