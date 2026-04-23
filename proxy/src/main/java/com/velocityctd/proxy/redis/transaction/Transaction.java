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

import com.velocityctd.proxy.redis.provider.RedisProvider;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a transaction process that sends {@link TransactionData} and produces a result
 * of type {@link R}. The result is delivered via a {@link CompletableFuture} that completes
 * when a reply is received or times out.
 *
 * <p>The transaction holds only the raw data to be sent. The provider is responsible for
 * wrapping the data in a transport envelope at publish time.</p>
 *
 * @param <T> the type of the sent data, implementing {@link TransactionData}
 * @param <R> the type of the expected response data
 */
public final class Transaction<T extends TransactionData<R>, R> {

  private static final Logger LOGGER = LoggerFactory.getLogger(Transaction.class);

  /**
   * The default timeout value (in seconds) used for transactions.
   */
  public static final int DEFAULT_TIMEOUT = 5;

  /**
   * The default time unit associated with {@link #DEFAULT_TIMEOUT}.
   */
  public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

  /**
   * Unique identifier assigned to this transaction.
   */
  private final UUID transactionId;

  /**
   * The raw data to be sent as part of this transaction.
   */
  private final T sentData;

  /**
   * The future that will be completed with the response data when a reply
   * is received, or completed exceptionally on timeout.
   */
  private final CompletableFuture<R> future = new CompletableFuture<>();

  /**
   * The timeout duration to use for this transaction, expressed in {@link #timeUnit}.
   */
  private int timeout = DEFAULT_TIMEOUT;

  /**
   * The time unit associated with {@link #timeout}.
   */
  private TimeUnit timeUnit = DEFAULT_TIME_UNIT;

  /**
   * Constructs a new {@link Transaction} with the given data.
   *
   * @param sentData the data to send
   */
  private Transaction(@NotNull T sentData) {
    this.transactionId = UUID.randomUUID();
    this.sentData = sentData;
  }

  /**
   * Creates a new {@link Transaction} wrapping the given data.
   *
   * @param data the data to send
   * @param <T> the type of the data
   * @param <R> the type of the expected response
   * @return a new transaction
   */
  public static <T extends TransactionData<R>, R> Transaction<T, R> of(@NotNull T data) {
    return new Transaction<>(data);
  }

  /**
   * Publish the {@link Transaction} to all subscribers on the redis and return a
   * {@link CompletableFuture} that will be completed with the response data.
   *
   * @param provider the {@link RedisProvider} to publish the transaction to
   * @return a future that completes with the response data or exceptionally on timeout
   *
   * @see RedisProvider#publish(Transaction, int, TimeUnit)
   */
  public CompletableFuture<R> publish(@NotNull RedisProvider provider) {
    provider.publish(this);
    return this.future;
  }

  /**
   * Gets the future that this Transaction will complete upon receiving a reply.
   *
   * @return the transaction's reply future
   */
  public CompletableFuture<R> getFuture() {
    return future;
  }

  /**
   * Set the timeout for the {@link Transaction}.
   *
   * @param timeout  the timeout in the given time unit
   * @param timeUnit the time unit of the timeout argument
   * @return itself for chaining
   */
  public Transaction<T, R> setTimeout(int timeout, TimeUnit timeUnit) {
    this.timeout = timeout;
    this.timeUnit = timeUnit;
    return this;
  }

  /**
   * Complete the {@link Transaction} with the given result data.
   * If the future has already been completed (e.g. by a previous reply or timeout),
   * this call is silently ignored.
   *
   * @param result the response data
   */
  @SuppressWarnings("unchecked")
  public void complete(Object result) {
    if (this.future.isDone()) {
      return;
    }

    try {
      this.future.complete((R) result);
    } catch (ClassCastException e) {
      LOGGER.warn("Transaction {} completed with unexpected result type '{}', expected a different type",
          this.transactionId, result == null ? "null" : result.getClass().getName(), e);
      this.future.completeExceptionally(e);
    }
  }

  /**
   * Timeout the {@link Transaction} by completing the future exceptionally.
   *
   * @apiNote This method is called automatically when the transaction times out, which
   *          can be configured using {@link Transaction#setTimeout(int, TimeUnit)}
   */
  public void timeout() {
    this.future.completeExceptionally(new TimeoutException("Transaction timed out"));
  }

  /**
   * Get the unique id of the {@link Transaction}.
   *
   * @return the unique id of the transaction
   */
  public UUID getTransactionId() {
    return transactionId;
  }

  /**
   * Get the raw data to be sent as part of this transaction.
   *
   * @return the sent data
   */
  public T getSentData() {
    return sentData;
  }

  /**
   * Get the timeout of the {@link Transaction}.
   *
   * @return the timeout of the transaction
   */
  public int getTimeout() {
    return timeout;
  }

  /**
   * Get the time unit of the {@link Transaction}.
   *
   * @return the time unit of the transaction
   */
  public TimeUnit getTimeUnit() {
    return timeUnit;
  }
}
