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

import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.ServerStatus;
import com.velocityctd.proxy.queue.RedisVelocityQueue;
import com.velocityctd.proxy.queue.RedisVelocityQueueEntry;
import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocityctd.proxy.queue.redis.packet.VelocityQueueSync;
import com.velocityctd.proxy.redis.depot.DepotEntry;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A Redis depot entry that persists the full state of a {@link VelocityQueue}.
 *
 * <p>Used exclusively for cold-start recovery: the master proxy writes queue state
 * to Redis periodically, and any proxy that starts up (or takes over as master) can
 * load the last known state from here. Live synchronization between running proxies
 * is handled via {@link VelocityQueueSync}
 * pub/sub packets - this depot is not used during normal operation.</p>
 */
public final class VelocityQueueDepotEntry extends DepotEntry<String, VelocityQueueDepotEntry> {

  /**
   * The ordered list of queue entries persisted for this queue.
   */
  private final List<RedisVelocityQueueEntry> entries;

  /**
   * The server status at the time of persistence.
   */
  private final ServerStatus serverStatus;

  /**
   * The queue state at the time of persistence.
   */
  private final QueueState state;

  /**
   * Constructs a {@link VelocityQueueDepotEntry} by capturing the current state of a queue.
   *
   * @param queue the queue to snapshot
   */
  public VelocityQueueDepotEntry(@NotNull RedisVelocityQueue queue) {
    super(queue.getName());

    //noinspection unchecked
    this.entries = new ArrayList<>((List<RedisVelocityQueueEntry>) (List<?>) queue.getInternalEntries());
    this.serverStatus = queue.getServerStatus();
    this.state = queue.getState();
  }

  /**
   * Returns the ordered list of queue entries.
   *
   * @return the queue entries
   */
  public List<RedisVelocityQueueEntry> getEntries() {
    return entries;
  }

  /**
   * Returns the persisted server status.
   *
   * @return the server status
   */
  public ServerStatus getServerStatus() {
    return serverStatus;
  }

  /**
   * Returns the persisted queue state.
   *
   * @return the queue state
   */
  public QueueState getState() {
    return state;
  }
}
