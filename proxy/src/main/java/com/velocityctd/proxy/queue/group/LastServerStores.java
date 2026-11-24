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

package com.velocityctd.proxy.queue.group;

import com.velocityctd.api.queue.LastServerStore;
import com.velocityctd.proxy.redis.VelocityRedis;
import org.jetbrains.annotations.Nullable;

/**
 * Factory methods for {@link LastServerStore} instances.
 */
public final class LastServerStores {

  private LastServerStores() {
    throw new AssertionError();
  }

  /**
   * Creates a store that uses Redis when available and in-memory storage otherwise.
   *
   * @param redis the proxy's Redis instance, or {@code null} when Redis is disabled
   * @return a last-server store
   */
  public static LastServerStore create(@Nullable VelocityRedis redis) {
    InMemoryLastServerStore memory = new InMemoryLastServerStore();
    if (redis == null) {
      return memory;
    }

    return new FallbackLastServerStore(new RedisLastServerStore(redis), memory);
  }
}
