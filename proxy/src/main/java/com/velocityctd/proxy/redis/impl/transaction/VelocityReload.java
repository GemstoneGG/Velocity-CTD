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

package com.velocityctd.proxy.redis.impl.transaction;

import com.velocityctd.proxy.redis.packet.typed.StringPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a transaction that reloads any proxy.
 */
public final class VelocityReload extends VelocityTransaction<StringPacket, Boolean> {

  /**
   * Constructs a new {@link VelocityReload} transaction.
   *
   * @param proxyId the id of the proxy to reload
   */
  public VelocityReload(final @NotNull String proxyId) {
    super(new StringPacket(proxyId));
  }
}
