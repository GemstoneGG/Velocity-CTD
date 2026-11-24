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
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link LastServerStore} that reads from a shared store first and keeps a local copy
 * for when the shared store has no value or is unavailable.
 *
 * <p>Writes made while the primary is unavailable are kept as pending records. On the next
 * read they are retried against the primary before it is trusted, so a stale Redis value
 * cannot overwrite a newer local value after a reconnect. Pending records expire after the
 * same thirty-day window as the Redis entries.</p>
 */
public final class FallbackLastServerStore implements LastServerStore {

  private static final long PENDING_EXPIRY_MS = TimeUnit.DAYS.toMillis(30);

  private final LastServerStore primary;
  private final LastServerStore fallback;
  private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();

  /**
   * Creates a store with the given primary and fallback implementations.
   *
   * @param primary  the store to try first
   * @param fallback the store used when the primary has no value
   */
  public FallbackLastServerStore(@NotNull LastServerStore primary, @NotNull LastServerStore fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  @Override
  public Optional<String> getLastServer(UUID playerId) {
    return read(playerId, "", false);
  }

  @Override
  public Optional<String> getLastServer(UUID playerId, String groupName) {
    return read(playerId, groupName, true);
  }

  @Override
  public void recordLastServer(UUID playerId, String serverName) {
    write(playerId, "", serverName, false);
  }

  @Override
  public void recordLastServer(UUID playerId, String groupName, String serverName) {
    write(playerId, groupName, serverName, true);
  }

  private Optional<String> read(UUID playerId, String groupName, boolean scoped) {
    String key = key(playerId, groupName);
    Pending waiting = pending.get(key);
    if (waiting != null && !waiting.expired()) {
      if (scoped) {
        primary.recordLastServer(playerId, groupName, waiting.serverName);
      } else {
        primary.recordLastServer(playerId, waiting.serverName);
      }

      Optional<String> primaryValue = scoped
          ? primary.getLastServer(playerId, groupName)
          : primary.getLastServer(playerId);
      if (waiting.serverName.equals(primaryValue.orElse(null))) {
        pending.remove(key);
        return primaryValue;
      }
      // The primary still holds an older value or is unavailable: the local write is newer.
      return Optional.of(waiting.serverName);
    }
    pending.remove(key);

    Optional<String> fromPrimary = scoped
        ? primary.getLastServer(playerId, groupName)
        : primary.getLastServer(playerId);
    if (fromPrimary.isPresent()) {
      if (scoped) {
        fallback.recordLastServer(playerId, groupName, fromPrimary.get());
      } else {
        fallback.recordLastServer(playerId, fromPrimary.get());
      }
      return fromPrimary;
    }

    return scoped
        ? fallback.getLastServer(playerId, groupName)
        : fallback.getLastServer(playerId);
  }

  private void write(UUID playerId, String groupName, String serverName, boolean scoped) {
    if (scoped) {
      fallback.recordLastServer(playerId, groupName, serverName);
      primary.recordLastServer(playerId, groupName, serverName);
    } else {
      fallback.recordLastServer(playerId, serverName);
      primary.recordLastServer(playerId, serverName);
    }
    pruneExpired();
    pending.put(key(playerId, groupName), new Pending(serverName, System.currentTimeMillis()));
  }

  private void pruneExpired() {
    pending.entrySet().removeIf(entry -> entry.getValue().expired());
  }

  private static String key(UUID playerId, String groupName) {
    return groupName + ':' + playerId;
  }

  private static final class Pending {
    private final String serverName;
    private final long writtenAtMs;

    private Pending(String serverName, long writtenAtMs) {
      this.serverName = serverName;
      this.writtenAtMs = writtenAtMs;
    }

    private boolean expired() {
      return System.currentTimeMillis() - writtenAtMs > PENDING_EXPIRY_MS;
    }
  }
}
