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

import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.packet.DataPacket;
import com.velocityctd.proxy.redis.packet.RedisPacket;
import com.velocityctd.proxy.redis.provider.RedisProvider;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a transaction process that has a {@link T sent-packet} and produces a result of
 * type {@link R}. The result is delivered via a {@link CompletableFuture} that completes when
 * a reply is received or times out.
 *
 * @param <T> the type of the sent {@link RedisPacket}
 * @param <R> the type of the expected response data
 */
public class Transaction<T extends RedisPacket, R> {

  /**
   * The default timeout value (in seconds) used for transactions.
   */
  public static final int DEFAULT_TIMEOUT = 5;

  /**
   * The default time unit associated with {@link #DEFAULT_TIMEOUT}.
   */
  public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

  /**
   * Shared logger for transaction-related debug and warning messages.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(Transaction.class);

  /**
   * Unique identifier assigned to this transaction.
   */
  private final UUID transactionId;

  /**
   * The packet sent as part of this transaction.
   */
  private final T sentPacket;

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
   * Constructs a new {@link Transaction} given an instance of the required sent-packet
   * and the expected response type.
   *
   * @param sentPacket   the sent-packet instance to publish
   */
  public Transaction(final @NotNull T sentPacket) {
    this.transactionId = UUID.randomUUID();
    this.sentPacket = sentPacket;
    this.sentPacket.setTransactionId(this.transactionId);
    this.sentPacket.setTransactionType(this.getClass().getName());
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
  public CompletableFuture<R> publish(final @NotNull RedisProvider provider) {
    provider.publish(this);
    return this.future;
  }

  /**
   * Publish the {@link Transaction} to all subscribers on the redis using the global
   * {@link VelocityRedis} provider instance.
   *
   * @return a future that completes with the response data or exceptionally on timeout
   *
   * @throws IllegalStateException if no Redis provider is available
   * @see RedisProvider#publish(Transaction, int, TimeUnit)
   */
  public CompletableFuture<R> publish() {
    final RedisProvider provider = VelocityRedis.INSTANCE.getProvider();
    if (provider == null) {
      throw new IllegalStateException("No redis instance has been provided");
    }

    return publish(provider);
  }

  /**
   * Set the timeout for the {@link Transaction}.
   *
   * @param timeout  the timeout in the given time unit
   * @param timeUnit the time unit of the timeout argument
   * @return itself for chaining
   */
  public Transaction<T, R> setTimeout(final int timeout, final TimeUnit timeUnit) {
    this.timeout = timeout;
    this.timeUnit = timeUnit;
    return this;
  }

  /**
   * Complete the {@link Transaction} with the data from the given reply packet.
   * If the future has already been completed (e.g. by a previous reply or timeout),
   * this call is silently ignored.
   *
   * @param replyPacket the reply packet containing the response data
   */
  public void complete(final RedisPacket replyPacket) {
    if (this.future.isDone()) {
      return;
    }

    if (replyPacket instanceof DataPacket dataPacket) {
      try {
        this.future.complete(dataPacket.getPayload());
      } catch (Exception e) {
        LOGGER.warn("Failed to deserialize reply data: {}", e.getMessage());
        this.future.completeExceptionally(e);
      }
    } else {
      LOGGER.warn("Reply packet is not a DataPacket: {}", replyPacket.getClass().getName());
      this.future.completeExceptionally(
          new IllegalStateException("Expected DataPacket reply, got " + replyPacket.getClass().getName()));
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
   * Get the sent-packet of the {@link Transaction}.
   *
   * @return the sent-packet of the transaction
   */
  public T getSentPacket() {
    return sentPacket;
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
