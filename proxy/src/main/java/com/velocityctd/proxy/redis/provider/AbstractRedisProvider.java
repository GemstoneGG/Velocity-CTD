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

package com.velocityctd.proxy.redis.provider;

import com.google.common.collect.ImmutableList;
import com.velocityctd.proxy.redis.handler.RouteHandler;
import com.velocityctd.proxy.redis.packet.DataPacket;
import com.velocityctd.proxy.redis.packet.PacketSerializer;
import com.velocityctd.proxy.redis.transaction.PendingTransactions;
import com.velocityctd.proxy.redis.transaction.Transaction;
import com.velocityctd.proxy.redis.transaction.TransactionData;
import com.velocityctd.proxy.redis.transaction.TransactionHandler;
import com.velocitypowered.api.scheduler.Scheduler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an abstract {@link RedisProvider} used to provide a common interface for all redis providers.
 *
 * @see LettuceProvider
 */
public abstract sealed class AbstractRedisProvider implements RedisProvider permits LettuceProvider {

  /**
   * Shared logger for all Redis provider implementations.
   */
  protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractRedisProvider.class);

  /**
   * Cache of pending {@link Transaction} instances, which are automatically timed out
   * via the associated {@link PendingTransactions} callback.
   */
  protected final PendingTransactions pendingTransactions;

  /**
   * The {@link PacketSerializer} instance used for packet (de)serialization.
   */
  protected final PacketSerializer packetSerializer;

  /**
   * Listeners notified whenever the Redis pub/sub connection is re-established after a drop.
   */
  private final List<Runnable> reconnectListeners = new CopyOnWriteArrayList<>();

  /**
   * The registry of all route registrations keyed by data class name.
   */
  @MonotonicNonNull
  protected final Map<String, RouteHandler<?>> routeHandlers;

  /**
   * The registry of all transaction handlers keyed by transaction class name.
   */
  @MonotonicNonNull
  protected final Map<String, TransactionHandler<?, ?>> transactionHandlers;

  /**
   * Constructs a new {@link AbstractRedisProvider}.
   *
   * @param scheduler the scheduler used for transaction timeout tasks
   * @param packetSerializer the serializer for packet (de)serialization
   */
  public AbstractRedisProvider(@NotNull Scheduler scheduler,
                               @NotNull PacketSerializer packetSerializer) {
    this.pendingTransactions = new PendingTransactions(scheduler);
    this.packetSerializer = packetSerializer;
    this.routeHandlers = new HashMap<>();
    this.transactionHandlers = new HashMap<>();
  }

  /**
   * Publishes a payload by wrapping it in a {@link DataPacket} and sending it
   * to the Redis channel.
   *
   * @param payload the payload to publish
   */
  @Override
  public void publish(@NotNull Object payload) {
    this.publishRaw(DataPacket.of(payload, packetSerializer));
  }

  /**
   * Publishes a transaction by wrapping its data in a {@link DataPacket} and registering
   * the transaction for timeout handling.
   *
   * @param transaction the transaction to publish
   * @param timeout the timeout value
   * @param timeUnit the time unit of the timeout
   */
  @Override
  public void publish(@NotNull Transaction<?, ?> transaction, int timeout, TimeUnit timeUnit) {
    DataPacket sentPacket = DataPacket.of(transaction.getSentData(), packetSerializer);
    sentPacket.setTransactionId(transaction.getTransactionId());

    pendingTransactions.put(transaction, timeout, timeUnit);

    this.publishRaw(sentPacket);
  }

  /**
   * Publishes a raw {@link DataPacket} to the Redis channel. Implemented by concrete providers.
   *
   * @param packet the packet to publish
   */
  protected abstract void publishRaw(@NotNull DataPacket packet);

  /**
   * Registers a route for a specific data type.
   *
   * @param routeHandler the route registration to add
   * @param <T> the type of data handled by the route
   */
  @Override
  public <T> void registerRoute(@NotNull RouteHandler<T> routeHandler) {
    Class<T> dataClass = routeHandler.getDataClass();

    if (this.routeHandlers.containsKey(dataClass.getName())) {
      LOGGER.debug("Route registration for '{}' already exists, overwriting", dataClass.getSimpleName());
    }

    this.routeHandlers.put(dataClass.getName(), routeHandler);
  }

  /**
   * Unregisters the route associated with the given data class, if present.
   *
   * @param dataClass the data class whose route should be removed
   * @param <T> the type of data handled by the route
   */
  @Override
  public <T> void unregisterRoute(@NotNull Class<T> dataClass) {
    if (this.routeHandlers.remove(dataClass.getName()) == null) {
      LOGGER.debug("Route registration for '{}' does not exist, ignoring", dataClass.getSimpleName());
    } else {
      LOGGER.debug("Unregistered route registration for '{}'", dataClass.getSimpleName());
    }
  }

  /**
   * Registers a {@link TransactionHandler} for a given {@link TransactionData} type.
   *
   * @param transactionHandler the handler to register
   */
  @Override
  public void registerTransaction(@NotNull TransactionHandler<?, ?> transactionHandler) {
    Class<?> dataClass = transactionHandler.getDataClass();

    if (this.transactionHandlers.containsKey(dataClass.getName())) {
      LOGGER.debug("Transaction handler for '{}' already exists, overwriting", dataClass.getSimpleName());
    }

    this.transactionHandlers.put(dataClass.getName(), transactionHandler);
  }

  /**
   * Unregisters the {@link TransactionHandler} associated with the given data class, if present.
   *
   * @param dataClass the data class whose handler should be removed
   */
  @Override
  public void unregisterTransaction(@NotNull Class<? extends TransactionData<?>> dataClass) {
    if (this.transactionHandlers.remove(dataClass.getName()) == null) {
      LOGGER.debug("Transaction handler for '{}' does not exist, ignoring", dataClass.getSimpleName());
    } else {
      LOGGER.debug("Unregistered transaction handler for '{}'", dataClass.getSimpleName());
    }
  }

  /**
   * Gets an immutable list of all registered {@link RouteHandler} instances.
   *
   * @return an immutable list of route registrations
   */
  @Override
  public @NotNull ImmutableList<@NotNull RouteHandler<?>> getRouteHandlers() {
    return ImmutableList.copyOf(this.routeHandlers.values());
  }

  /**
   * Registers a listener to be called whenever the Redis pub/sub connection is re-established
   * after a disconnection.
   *
   * @param listener the callback to invoke on reconnect
   */
  public void addReconnectListener(@NotNull Runnable listener) {
    this.reconnectListeners.add(listener);
  }

  /**
   * Invokes all registered reconnect listeners.
   * Called by the provider implementation when the pub/sub channel is re-subscribed.
   */
  protected void fireReconnectListeners() {
    for (Runnable listener : this.reconnectListeners) {
      listener.run();
    }
  }
}
