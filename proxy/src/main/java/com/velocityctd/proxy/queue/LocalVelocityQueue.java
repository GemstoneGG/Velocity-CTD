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

package com.velocityctd.proxy.queue;

import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.api.queue.QueueState;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import org.jetbrains.annotations.NotNull;

/**
 * Concrete in-memory queue used when Redis is disabled. Binds {@code E} to the base
 * {@link VelocityQueueEntry} type and supplies the matching {@link #createEntry} factory
 * that the abstract {@link VelocityQueue} parent requires.
 */
public final class LocalVelocityQueue extends VelocityQueue<VelocityQueueEntry> {

  public LocalVelocityQueue(VelocityServer server, VelocityQueueManager manager,
                            VelocityRegisteredServer backend, QueueState initialState) {
    super(server, manager, backend, initialState);
  }

  @Override
  protected VelocityQueueEntry createEntry(@NotNull QueueEntryData data) {
    return new VelocityQueueEntry(server, this, data);
  }
}
