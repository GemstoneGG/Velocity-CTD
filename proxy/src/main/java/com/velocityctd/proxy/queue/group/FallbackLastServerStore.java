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
import org.jetbrains.annotations.NotNull;

/**
 * A {@link LastServerStore} that reads from a shared store first and keeps a local copy
 * for when the shared store has no value or is unavailable.
 */
public final class FallbackLastServerStore implements LastServerStore {

  private final LastServerStore primary;
  private final LastServerStore fallback;

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
    Optional<String> fromPrimary = primary.getLastServer(playerId);
    if (fromPrimary.isPresent()) {
      return fromPrimary;
    }

    return fallback.getLastServer(playerId);
  }

  @Override
  public void recordLastServer(UUID playerId, String serverName) {
    fallback.recordLastServer(playerId, serverName);
    primary.recordLastServer(playerId, serverName);
  }
}
