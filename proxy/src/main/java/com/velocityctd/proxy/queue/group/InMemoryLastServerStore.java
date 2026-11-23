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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory {@link LastServerStore} used when no shared store is available.
 *
 * <p>Entries live only for the lifetime of this proxy process.</p>
 */
public final class InMemoryLastServerStore implements LastServerStore {

  private final ConcurrentMap<UUID, String> lastServers = new ConcurrentHashMap<>();

  @Override
  public Optional<String> getLastServer(UUID playerId) {
    return Optional.ofNullable(lastServers.get(playerId));
  }

  @Override
  public void recordLastServer(UUID playerId, String serverName) {
    lastServers.put(playerId, serverName);
  }
}
