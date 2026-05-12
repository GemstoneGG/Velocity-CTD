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

package com.velocitypowered.proxy.adventure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Scheduler;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.NotNull;

/**
 * Click callback manager.
 */
public final class ClickCallbackManager {

  public static final ClickCallbackManager INSTANCE = new ClickCallbackManager();

  public static final String COMMAND_LABEL = "velocity:callback";

  static final String COMMAND = "/" + COMMAND_LABEL + " ";

  private final AtomicBoolean hadRegistrations = new AtomicBoolean(false);

  private volatile @MonotonicNonNull Runnable onFirstRegistration;

  private final Cache<UUID, RegisteredCallback> registrations = Caffeine.newBuilder()
      .expireAfter(new Expiry<UUID, RegisteredCallback>() {
        @Override
        public long expireAfterCreate(@NotNull UUID key, @NotNull RegisteredCallback value, long currentTime) {
          return value.duration().toNanos();
        }

        @Override
        public long expireAfterUpdate(@NotNull UUID key, @NotNull RegisteredCallback value, long currentTime,
                                      @NonNegative long currentDuration) {
          return currentDuration;
        }

        @Override
        public long expireAfterRead(@NotNull UUID key, @NotNull RegisteredCallback value, long currentTime,
                                    @NonNegative long currentDuration) {
          AtomicInteger remainingUses = value.remainingUses();
          if (remainingUses != null && remainingUses.get() <= 0) {
            return 0;
          }
          return currentDuration;
        }
      })
      .scheduler(Scheduler.systemScheduler())
      .build();

  private ClickCallbackManager() {
  }

  /**
   * Sets a listener that is invoked the first time a callback is registered.
   *
   * @param listener the listener to invoke on the first registration
   * @throws IllegalStateException if a listener has already been set
   */
  public void setOnFirstRegistration(Runnable listener) {
    if (this.onFirstRegistration != null) {
      throw new IllegalStateException("A first-registration listener has already been set");
    }
    this.onFirstRegistration = listener;
  }

  /**
   * Returns whether any callback has ever been registered.
   *
   * @return {@code true} if at least one callback has been registered, {@code false} otherwise
   */
  public boolean hasHadRegistrations() {
    return hadRegistrations.get();
  }

  /**
   * Run a callback.
   *
   * @param audience the audience
   * @param id       the callback's ID
   * @return {@code true} if the callback was run, {@code false} if not
   */
  public boolean runCallback(Audience audience, UUID id) {
    RegisteredCallback callback = this.registrations.getIfPresent(id);
    if (callback != null && callback.tryUse()) {
      callback.callback().accept(audience);
      return true;
    }

    return false;
  }

  /**
   * Registers a click callback.
   *
   * @param callback the callback to register
   * @param options  associated options
   * @return the callback ID
   */
  public UUID register(ClickCallback<Audience> callback,
                       ClickCallback.Options options) {
    UUID id = UUID.randomUUID();
    RegisteredCallback registration = new RegisteredCallback(options.lifetime(), options.uses(), callback);
    this.registrations.put(id, registration);

    boolean alreadyHadRegistrations = hadRegistrations.getAndSet(true);
    if (!alreadyHadRegistrations) {
      Runnable listener = this.onFirstRegistration;
      if (listener != null) {
        listener.run();
      }
    }

    return id;
  }
}
