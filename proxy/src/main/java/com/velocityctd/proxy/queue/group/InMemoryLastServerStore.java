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
import java.util.concurrent.TimeUnit;

/**
 * An in-memory {@link LastServerStore} used when no shared store is available.
 *
 * <p>Entries expire after thirty days. Expired entries are removed on every read and
 * write, so the map does not grow without bound during long proxy sessions.</p>
 */
public final class InMemoryLastServerStore implements LastServerStore {

  private static final long EXPIRY_MILLIS = TimeUnit.DAYS.toMillis(30L);
  private static final String UNSCOPED = "";

  private final ConcurrentMap<StoreKey, Entry> lastServers = new ConcurrentHashMap<>();

  @Override
  public Optional<String> getLastServer(UUID playerId) {
    return read(playerId, UNSCOPED);
  }

  @Override
  public Optional<String> getLastServer(UUID playerId, String groupName) {
    return read(playerId, scope(groupName));
  }

  @Override
  public void recordLastServer(UUID playerId, String serverName) {
    write(playerId, UNSCOPED, serverName);
  }

  @Override
  public void recordLastServer(UUID playerId, String groupName, String serverName) {
    write(playerId, scope(groupName), serverName);
  }

  private Optional<String> read(UUID playerId, String scope) {
    pruneExpired();
    StoreKey key = new StoreKey(playerId, scope);
    Entry entry = lastServers.get(key);
    if (entry == null) {
      return Optional.empty();
    }

    if (entry.expiresAt() <= System.currentTimeMillis()) {
      lastServers.remove(key, entry);
      return Optional.empty();
    }

    return Optional.of(entry.serverName());
  }

  private void write(UUID playerId, String scope, String serverName) {
    pruneExpired();
    StoreKey key = new StoreKey(playerId, scope);
    if (serverName == null || serverName.isEmpty()) {
      lastServers.remove(key);
      return;
    }

    lastServers.put(key, new Entry(serverName, System.currentTimeMillis() + EXPIRY_MILLIS));
  }

  private void pruneExpired() {
    long now = System.currentTimeMillis();
    lastServers.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
  }

  private static String scope(String groupName) {
    return groupName == null ? UNSCOPED : groupName;
  }

  private record StoreKey(UUID playerId, String scope) {
  }

  private record Entry(String serverName, long expiresAt) {
  }
}
