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

import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a handler for incoming {@link TransactionData}.
 *
 * <p>A {@code TransactionHandler} knows how to transform incoming data
 * into a response of type {@code R}, and is associated with a specific
 * {@link TransactionData} class.</p>
 *
 * @param <T> the type of the data handled by this handler
 * @param <R> the type of the response data produced by this handler
 */
public abstract class TransactionHandler<T extends TransactionData<R>, R> {

  /**
   * The data class that this handler is responsible for.
   */
  private final Class<T> dataClass;

  /**
   * Constructs a new {@link TransactionHandler}.
   *
   * @param dataClass the class of the data this handler processes
   */
  public TransactionHandler(@NotNull Class<T> dataClass) {
    this.dataClass = dataClass;
  }

  /**
   * Handles the given data and produces a response asynchronously.
   *
   * @param data the incoming data to handle
   * @return a future containing the response data, or a future completing to {@code null}
   *         if no reply is required
   */
  public abstract @Nullable CompletableFuture<R> handleData(T data);

  /**
   * Gets the data class associated with this handler.
   *
   * @return the data class
   */
  public Class<T> getDataClass() {
    return dataClass;
  }
}
