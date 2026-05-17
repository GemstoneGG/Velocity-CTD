/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.redis.transaction;

import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for data types that can participate in a request-reply
 * {@link Transaction}. The type parameter {@code R} declares the expected
 * response type, enabling compile-time type safety when publishing
 * transactions.
 *
 * <p>Implementing classes are typically records that carry the request data:</p>
 * <pre>{@code
 * public record GetPlayerPing(String username) implements TransactionData<Long> {}
 * }</pre>
 *
 * @param <R> the type of the expected response
 */
public interface TransactionData<R> {

  /**
   * Returns the class of the response type R. Implementations should return the same
   * {@code Class<R>} that matches their declared R type parameter — this lets {@link
   * Transaction} perform type-safe deserialization without unchecked casts.
   */
  @NotNull Class<R> responseClass();
}
