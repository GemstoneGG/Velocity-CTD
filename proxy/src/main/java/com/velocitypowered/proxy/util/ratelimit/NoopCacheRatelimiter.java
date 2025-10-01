/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

package com.velocitypowered.proxy.util.ratelimit;

import org.jetbrains.annotations.NotNull;

/**
 * A {@link Ratelimiter} that does no rate-limiting.
 */
enum NoopCacheRatelimiter implements Ratelimiter<Object> {

  /**
   * Singleton instance of the {@link NoopCacheRatelimiter}, which performs no rate limiting.
   *
   * <p>This instance always allows all operations unconditionally by returning {@code true}
   * for every {@link #attempt(Object)} call, effectively disabling rate limiting logic.</p>
   */
  INSTANCE;

  @Override
  public boolean attempt(final @NotNull Object key) {
    return true;
  }
}
