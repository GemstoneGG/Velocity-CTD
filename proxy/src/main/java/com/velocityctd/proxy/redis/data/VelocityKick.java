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

package com.velocityctd.proxy.redis.data;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Data record used to remotely kick a player from another proxy.
 *
 * @param uniqueId the unique identifier of the player being kicked
 * @param component the disconnect message
 * @param targetProxyId the proxy ID that should process this kick, or {@code null} for all proxies
 */
public record VelocityKick(UUID uniqueId, Component component, @Nullable String targetProxyId) {

  /**
   * Constructs a new {@link VelocityKick} that targets all proxies.
   *
   * @param uniqueId the player's unique ID
   * @param component the disconnect message
   */
  public VelocityKick(UUID uniqueId, Component component) {
    this(uniqueId, component, null);
  }
}
