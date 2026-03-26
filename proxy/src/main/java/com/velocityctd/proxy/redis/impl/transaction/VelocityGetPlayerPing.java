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
 * Represents a transaction that gets the ping of a player.
 */
public final class VelocityGetPlayerPing extends VelocityTransaction<StringPacket, Long> {

  /**
   * Constructs a new {@link VelocityGetPlayerPing} transaction.
   *
   * @param username the username of the player to get the ping of
   */
  public VelocityGetPlayerPing(final @NotNull String username) {
    super(new StringPacket(username));
  }
}
