/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.redis.provider;

import com.google.common.collect.ImmutableList;
import com.velocityctd.proxy.redis.depot.Depot;
import com.velocityctd.proxy.redis.depot.DepotEntry;
import com.velocityctd.proxy.redis.handler.RouteHandler;
import com.velocityctd.proxy.redis.transaction.Transaction;
import com.velocityctd.proxy.redis.transaction.TransactionData;
import com.velocityctd.proxy.redis.transaction.TransactionHandler;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Redis provider.
 */
public sealed interface RedisProvider permits AbstractRedisProvider {

  /**
   * Gets the namespace used for all Redis keys and channels.
   *
   * @return the Redis namespace
   */
  @NotNull String getNamespace();

  /**
   * Restart the Redis provider.
   */
  void restart();

  /**
   * Disconnect the Redis provider.
   */
  void disconnect();

  /**
   * Publish a payload to the channel on the Redis, wrapping it in a
   * {@link com.velocityctd.proxy.redis.packet.DataPacket}.
   *
   * @param payload the payload to publish
   */
  void publish(@NotNull Object payload);

  /**
   * Publish a {@link Transaction} to all subscribers on the Redis.
   *
   * @param transaction the transaction to publish
   * @param timeout     the timeout in seconds (default = 5)
   * @param timeUnit    the time unit of the timeout (default = seconds)
   */
  void publish(@NotNull Transaction<?, ?> transaction, int timeout, TimeUnit timeUnit);

  /**
   * Publish a {@link Transaction} to all subscribers on the Redis.
   *
   * @param transaction the transaction to publish
   * @see #publish(Transaction, int, TimeUnit)
   */
  default void publish(@NotNull Transaction<?, ?> transaction) {
    publish(transaction, transaction.getTimeout(), transaction.getTimeUnit());
  }

  /**
   * Register a {@link RouteHandler} for a specific data class.
   *
   * @param routeHandler the route registration to register
   * @param <T>               the type of the data
   */
  <T> void registerRoute(@NotNull RouteHandler<T> routeHandler);

  /**
   * Unregister a {@link RouteHandler} for a specific data class, if it exists.
   *
   * @param dataClass the class of the data to unregister
   * @param <T>       the type of the data
   */
  <T> void unregisterRoute(@NotNull Class<T> dataClass);

  /**
   * Get an immutable list of all route registrations.
   *
   * @return the immutable list of all route registrations
   */
  @NotNull ImmutableList<@NotNull RouteHandler<?>> getRouteHandlers();

  /**
   * Register a {@link TransactionHandler} for a specific transaction class.
   *
   * @param transactionHandler the transaction handler to register
   */
  void registerTransaction(@NotNull TransactionHandler<?, ?> transactionHandler);

  /**
   * Unregister a {@link TransactionHandler} for a specific data class, if it exists.
   *
   * @param dataClass the data class whose handler should be removed
   */
  void unregisterTransaction(@NotNull Class<? extends TransactionData<?>> dataClass);

  /**
   * Create a {@link Depot} that can hold objects of a specific type.
   *
   * @param <K> the type of the key in the depot
   * @param <V> the type of the object value in the depot
   * @return a new depot instance
   */
  <K, V extends DepotEntry<K, V>> @NotNull Depot<K, V> createDepot(Class<V> valueClass);

  /**
   * Gets the value associated with a key.
   *
   * @param key the key to look up
   * @return the value, or {@code null} if none exists
   */
  @Nullable String get(@NotNull String key);

  /**
   * Sets a key with an expiry time in seconds.
   *
   * @param key        the key to set
   * @param value      the value to store
   * @param ttlSeconds the time-to-live in seconds
   */
  void setWithExpiry(@NotNull String key, @NotNull String value, long ttlSeconds);

  /**
   * Atomically sets the key to the given value only if it does not already exist.
   *
   * @param key   the key to set
   * @param value the value to store
   */
  void setIfAbsent(@NotNull String key, @NotNull String value);

  /**
   * Checks whether a key exists in Redis.
   *
   * @param key the key to check
   * @return {@code true} if the key exists, otherwise {@code false}
   */
  boolean existsKey(@NotNull String key);

  /**
   * Deletes a key from Redis.
   *
   * @param key the key to delete
   */
  void deleteKey(@NotNull String key);

  /**
   * Check if the Redis provider is connected.
   *
   * @return true if the Redis provider is connected, false otherwise
   */
  boolean isConnected();
}
