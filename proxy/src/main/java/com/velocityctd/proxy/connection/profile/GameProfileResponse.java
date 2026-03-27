/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

package com.velocityctd.proxy.connection.profile;

import com.velocitypowered.api.util.GameProfile;
import java.util.NoSuchElementException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class GameProfileResponse {

  @Nullable
  private final GameProfile gameProfile;

  @NonNull
  private final Status status;

  GameProfileResponse(@Nullable GameProfile gameProfile, @NonNull Status status) {
    if (status.success() && gameProfile == null) {
      throw new IllegalArgumentException("Expected a non-null GameProfile for a successful status.");
    }
    if (!status.success() && gameProfile != null) {
      throw new IllegalArgumentException("Expected a null GameProfile for a non-successful status.");
    }

    this.gameProfile = gameProfile;
    this.status = status;
  }

  @NonNull
  public GameProfile gameProfile() {
    if (gameProfile == null) {
      throw new NoSuchElementException("No game profile fetched.");
    }

    return gameProfile;
  }

  @NonNull
  public Status status() {
    return status;
  }

  public enum Status {

    SUCCESS,
    SUCCESS_CACHED,
    ERROR_OFFLINE_USER,
    ERROR_AUTH_DOWN;

    public boolean success() {
      return this == SUCCESS || this == SUCCESS_CACHED;
    }
  }
}
