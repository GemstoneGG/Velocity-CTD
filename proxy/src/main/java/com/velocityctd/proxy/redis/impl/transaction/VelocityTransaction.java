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

import com.velocityctd.proxy.redis.packet.RedisPacket;
import com.velocityctd.proxy.redis.transaction.Transaction;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an extension of the {@link Transaction} for the VelocityRedis module.
 *
 * @param <T> the type of Redis packet being sent
 * @param <R> the type of response data expected
 */
public abstract class VelocityTransaction<T extends RedisPacket, R> extends Transaction<T, R> {

  /**
   * Constructs a new {@link VelocityTransaction}.
   *
   * @param sentPacket   the packet to send
   */
  public VelocityTransaction(final @NotNull T sentPacket) {
    super(sentPacket);
  }
}
