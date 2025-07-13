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

import java.util.concurrent.TimeUnit;

/**
 * Factory to create rate limiters.
 */
public final class Ratelimiters {

  private Ratelimiters() {
    throw new AssertionError();
  }

  /**
   * Creates a {@link Ratelimiter} with a cooldown window in milliseconds.
   *
   * <p>If {@code ms} is {@code 0} or negative, a no-op rate limiter will be returned
   * that allows all keys through unconditionally.</p>
   *
   * @param ms the cooldown duration in milliseconds
   * @return a {@link Ratelimiter} enforcing the given cooldown, or a no-op limiter if disabled
   */
  public static Ratelimiter createWithMilliseconds(final long ms) {
    return ms <= 0 ? NoopCacheRatelimiter.INSTANCE : new CaffeineCacheRatelimiter(ms, TimeUnit.MILLISECONDS);
  }
}
