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

package com.velocityctd.proxy.redis.transaction;

import com.google.common.base.Preconditions;
import com.velocityctd.proxy.redis.packet.DataPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a handler for a {@link Transaction}.
 *
 * <p>A {@code TransactionHandler} knows how to transform incoming data
 * into a response of type {@code R}, and is associated with a specific
 * {@link Transaction} class. The response is automatically wrapped in a
 * {@link DataPacket} for transmission.</p>
 *
 * @param <T> the type of the sent data handled by this transaction
 * @param <R> the type of the response data produced by this handler
 */
public abstract class TransactionHandler<T, R> {

  /**
   * The transaction class that this handler is responsible for.
   */
  private final Class<? extends Transaction<T, R>> transactionClass;

  /**
   * Constructs a new {@link TransactionHandler}.
   *
   * @param transactionClass the class of the transaction
   */
  public TransactionHandler(final @NotNull Class<? extends Transaction<T, R>> transactionClass) {
    this.transactionClass = transactionClass;
  }

  /**
   * Handles the given data and produces a response, if needed.
   *
   * @param data the incoming data to handle
   * @return the response data, or {@code null} if no reply is required
   */
  public abstract @Nullable R handleData(T data);

  /**
   * Creates the reply {@link DataPacket} for the given incoming {@link DataPacket}, if any.
   *
   * <p>This method extracts the payload from the incoming packet, delegates to
   * {@link #handleData(Object)} and, if a response is returned, wraps it in a
   * {@link DataPacket} and propagates the transaction ID from the incoming packet.</p>
   *
   * @param incomingPacket the packet to create the reply from
   * @return {@code null} if no reply is needed, otherwise a {@link DataPacket} containing the response
   */
  @SuppressWarnings("unchecked")
  public @Nullable DataPacket getReplyPacket(final @NotNull DataPacket incomingPacket) {
    final T data = (T) incomingPacket.getPayload();
    final R result = this.handleData(data);
    if (result == null) {
      return null;
    }

    final DataPacket replyPacket = new DataPacket(result);
    Preconditions.checkNotNull(incomingPacket.getTransactionId());
    replyPacket.setTransactionId(incomingPacket.getTransactionId());
    replyPacket.setReply(true);

    return replyPacket;
  }

  /**
   * Gets the class of the {@link Transaction} associated with this handler.
   *
   * @return the transaction class
   */
  public Class<? extends Transaction<T, R>> getTransactionClass() {
    return transactionClass;
  }
}
