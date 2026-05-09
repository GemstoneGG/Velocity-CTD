/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.queue.redis.depot;

import com.velocityctd.proxy.queue.RedisVelocityQueue;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.depot.AbstractDepotService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages persistence of {@link VelocityQueueDepotEntry} objects in Redis.
 *
 * <p>This service is used only for cold-start state recovery. The master proxy
 * writes queue snapshots here periodically; a newly-started or newly-promoted
 * master proxy reads them back on initialization.</p>
 */
public final class VelocityQueueDepotService
    extends AbstractDepotService<String, VelocityQueueDepotEntry> {

  /**
   * Constructs a new {@link VelocityQueueDepotService}.
   *
   * @param redis the active Redis integration
   */
  public VelocityQueueDepotService(@NotNull VelocityRedis redis) {
    super(VelocityQueueDepotEntry.class, redis.getProvider());
  }

  /**
   * Persists the current state of the given queue to Redis.
   *
   * @param queue the queue to persist
   */
  public void upsertQueue(@NotNull RedisVelocityQueue queue) {
    this.depot.upsert(new VelocityQueueDepotEntry(queue));
  }

  /**
   * Retrieves the persisted state for the given server's queue, or {@code null} if none exists.
   *
   * @param serverName the name of the server
   * @return the depot entry, or {@code null}
   */
  public @Nullable VelocityQueueDepotEntry getQueueEntry(@NotNull String serverName) {
    return this.depot.get(serverName);
  }
}
