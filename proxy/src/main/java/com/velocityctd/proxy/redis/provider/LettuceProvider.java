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

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.velocityctd.proxy.redis.depot.Depot;
import com.velocityctd.proxy.redis.depot.DepotEntry;
import com.velocityctd.proxy.redis.handler.RouteHandler;
import com.velocityctd.proxy.redis.packet.DataPacket;
import com.velocityctd.proxy.redis.packet.PacketSerializer;
import com.velocityctd.proxy.redis.transaction.Transaction;
import com.velocityctd.proxy.redis.transaction.TransactionHandler;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the Lettuce implementation of the {@link RedisProvider} interface.
 */
public final class LettuceProvider extends AbstractRedisProvider {

  /**
   * The current Redis protocol version.
   */
  public static final String VERSION = "v1";

  /**
   * Logger used for all Lettuce provider operations and diagnostics.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(LettuceProvider.class);

  /**
   * The namespace applied to all Redis keys and channels.
   */
  private static final String NAMESPACE = System.getProperty("velocity.redis-namespace", "velocity");

  /**
   * Redis key template for the pub/sub channel.
   */
  private static final String CHANNEL_TEMPLATE = "%s:%s:channel";

  /**
   * Redis key template for the depot names.
   */
  private static final String DEPOT_TEMPLATE = "%s:%s:depot:%s";

  /**
   * The underlying Lettuce {@link RedisClient} used to establish Redis connections.
   */
  private final RedisClient client;

  /**
   * The Redis Pub/Sub channel name used for all Velocity Redis communication.
   */
  private final String channel;

  /**
   * The asynchronous Redis Pub/Sub command API used to publish packets and manage subscriptions.
   */
  private RedisPubSubAsyncCommands<String, String> publisher;

  /**
   * A synchronous pub/sub connection used for raw key operations such as setting keys with expiry,
   * checking key existence, and deleting keys.
   */
  private RedisPubSubCommands<String, String> syncPublisher;

  /**
   * A single shared connection used by all {@link LettuceDepot} instances for hash
   * operations. Depots do not use pub/sub, so one regular connection replaces the
   * previous per-depot connections. The connection itself is held for lifecycle
   * management; {@link #depotCommands} is its synchronous command view.
   */
  private StatefulRedisConnection<String, String> depotConnection;

  /**
   * The synchronous command view of {@link #depotConnection}, shared by all
   * {@link LettuceDepot} instances for their Redis hash operations.
   */
  private RedisCommands<String, String> depotCommands;

  /**
   * Constructs a new {@link LettuceProvider}.
   *
   * @param config the {@link VelocityConfiguration.Redis} instance to use for connection credentials
   * @param scheduler the scheduler used for transaction timeout tasks
   * @param packetSerializer the serializer for packet (de)serialization
   */
  public LettuceProvider(VelocityConfiguration.Redis config,
                         @NotNull Scheduler scheduler,
                         @NotNull PacketSerializer packetSerializer) {
    super(scheduler, packetSerializer, NAMESPACE);

    this.channel = CHANNEL_TEMPLATE.formatted(NAMESPACE, VERSION);

    this.client = RedisClient.create(RedisURI.Builder.redis(config.getHost(), config.getPort())
            .withAuthentication(Objects.requireNonNullElse(config.getUsername(), ""),
                    Objects.requireNonNullElse(config.getPassword(), ""))
            .withSsl(config.isUseSsl())
            .build());
  }

  /**
   * Restarts the underlying Redis Pub/Sub connection and resubscribes to the channel.
   *
   * <p>If an existing publisher connection is present, it is closed before a new
   * connection is created.</p>
   */
  @Override
  public void restart() {
    final RedisPubSubAsyncCommands<String, String> oldPublisher = this.publisher;
    final RedisPubSubCommands<String, String> oldSyncPublisher = this.syncPublisher;
    final StatefulRedisConnection<String, String> oldDepotConnection = this.depotConnection;

    StatefulRedisPubSubConnection<String, String> connection = this.client.connectPubSub();

    // Tracks whether the initial subscribe has completed. The first subscribed() callback is
    // the initial subscribe; every subsequent one is a re-subscribe after a reconnect.
    AtomicBoolean subscribedOnce = new AtomicBoolean(false);

    connection.addListener(new RedisPubSubAdapter<>() {
      @Override
      public void subscribed(String channel, long count) {
        if (LettuceProvider.this.channel.equals(channel) && subscribedOnce.getAndSet(true)) {
          // Re-subscribe after a reconnect, notify listeners so they can reload state.
          fireReconnectListeners();
        }
      }

      @Override
      public void message(String channel, String message) {
        if (!channel.equals(LettuceProvider.this.channel)) {
          return;
        }

        DataPacket dataPacket = packetSerializer.deserialize(message);
        if (dataPacket == null) {
          LOGGER.warn("Received a null packet from channel '{}', ignoring", channel);
          return;
        }

        if (dataPacket.isOneWay()) {
          handleOneWay(dataPacket);
          return;
        }

        if (!dataPacket.isReply()) {
          handleTransactionRequest(dataPacket);
        } else {
          handleTransactionReply(dataPacket);
        }
      }
    });

    RedisPubSubAsyncCommands<String, String> newPublisher = connection.async();
    newPublisher.subscribe(this.channel);
    StatefulRedisConnection<String, String> newDepotConnection = this.client.connect();

    this.publisher = newPublisher;
    this.syncPublisher = connection.sync();
    this.depotConnection = newDepotConnection;
    this.depotCommands = newDepotConnection.sync();

    if (oldPublisher != null) {
      oldPublisher.getStatefulConnection().close();
    }

    if (oldSyncPublisher != null) {
      oldSyncPublisher.getStatefulConnection().close();
    }

    if (oldDepotConnection != null) {
      oldDepotConnection.close();
    }

    LOGGER.info("Connected to Lettuce Redis Server on channel '{}'", this.channel);
  }

  /**
   * Disconnects from the Redis server by closing the Pub/Sub connection and shutting down the client.
   *
   * <p>If no active connection exists, a warning is logged and the call is ignored.</p>
   */
  @Override
  public void disconnect() {
    if (!this.isConnected()) {
      LOGGER.warn("Attempted to disconnect from Redis, but no connection was established");
      return;
    }

    if (publisher.getStatefulConnection().isOpen()) {
      publisher.getStatefulConnection().close();
    }
    publisher = null;

    if (syncPublisher.getStatefulConnection().isOpen()) {
      syncPublisher.getStatefulConnection().close();
    }
    syncPublisher = null;

    if (depotConnection != null && depotConnection.isOpen()) {
      depotConnection.close();
    }
    depotConnection = null;
    depotCommands = null;

    this.client.shutdown();

    LOGGER.info("Disconnected from Lettuce Redis Server on channel '{}'", this.channel);
  }

  /**
   * Publishes the given {@link DataPacket} to the configured Redis channel.
   *
   * <p>If the publisher has not been initialized yet, a warning is logged and the packet is not sent.</p>
   *
   * @param packet the packet to publish
   */
  @Override
  protected void publishRaw(@NotNull DataPacket packet) {
    if (this.publisher == null) {
      LOGGER.warn("Attempted to publish a packet to channel '{}' but the publisher is not initialized", this.channel);
      return;
    }

    this.publisher.publish(this.channel, packetSerializer.serialize(packet)).whenComplete((received, throwable) -> {
      if (throwable != null) {
        LOGGER.warn("Failed to publish packet to '{}' channel", this.channel, throwable);
      }
    });
  }

  /**
   * Creates a new {@link Depot} associated with this Redis provider backed by Lettuce.
   *
   * @param valueClass the class of the values stored in the depot
   * @param <K> the key type used by the depot
   * @param <V> the depot entry type
   * @return a new {@link LettuceDepot} instance
   */
  @Override
  @Contract(value = "_ -> new", pure = true)
  public <K, V extends DepotEntry<K, V>> @NotNull Depot<K, V> createDepot(Class<V> valueClass) {
    return new LettuceDepot<>(valueClass);
  }

  /**
   * Determines whether this provider is currently connected to Redis.
   *
   * @return {@code true} if the publisher exists and its connection is open, otherwise {@code false}
   */
  @Override
  public boolean isConnected() {
    return this.publisher != null && this.publisher.getStatefulConnection().isOpen();
  }

  /**
   * Handles a one-way {@link DataPacket} by routing it through a registered {@link RouteHandler}, if present.
   *
   * @param dataPacket the one-way packet to handle
   */
  private void handleOneWay(@NotNull DataPacket dataPacket) {
    RouteHandler<?> routeHandler = routeHandlers.get(dataPacket.getPayloadType());
    if (routeHandler == null) {
      LOGGER.warn("Received a packet of type '{}' from channel '{}', but no route registration exists, ignoring",
              dataPacket.getPayloadType(), this.channel);
      return;
    }

    try {
      routeHandler.dispatch(dataPacket, packetSerializer);
    } catch (Throwable t) {
      LOGGER.warn("Failed to handle one way packet of type '{}'.", dataPacket.getPayloadType(), t);
    }
  }

  /**
   * Handles an incoming transaction request by extracting the payload, delegating to the
   * registered {@link TransactionHandler}, and wrapping the result in a reply {@link DataPacket}.
   *
   * @param dataPacket the incoming transaction request packet
   */
  private void handleTransactionRequest(@NotNull DataPacket dataPacket) {
    TransactionHandler<?, ?> transactionHandler = transactionHandlers.get(dataPacket.getPayloadType());
    if (transactionHandler == null) {
      return;
    }

    CompletableFuture<?> future = transactionHandler.dispatch(dataPacket, packetSerializer);
    if (future == null) {
      return;
    }

    future.thenAccept(result -> {
      if (result == null) {
        return;
      }

      DataPacket replyPacket = DataPacket.of(result, packetSerializer);
      replyPacket.setTransactionId(Preconditions.checkNotNull(dataPacket.getTransactionId()));
      replyPacket.setReply(true);

      this.publishRaw(replyPacket);
    }).exceptionally(throwable -> {
      LOGGER.warn("Transaction handler for '{}' completed exceptionally", dataPacket.getPayloadType(), throwable);
      return null;
    });
  }

  /**
   * Handles an incoming transaction reply by extracting the payload and completing
   * the pending {@link Transaction}.
   *
   * @param dataPacket the incoming transaction reply packet
   */
  private void handleTransactionReply(@NotNull DataPacket dataPacket) {
    UUID transactionId = Preconditions.checkNotNull(dataPacket.getTransactionId());

    Transaction<?, ?> transaction = pendingTransactions.remove(transactionId);
    if (transaction == null) {
      return;
    }

    transaction.completeFrom(dataPacket, packetSerializer);
  }

  /**
   * Gets the value associated with a key using the regular (non-pub/sub) connection.
   *
   * @param key the key to look up
   * @return the value, or {@code null} if none exists
   */
  @Override
  public @Nullable String get(@NotNull String key) {
    if (this.syncPublisher == null) {
      LOGGER.warn("Attempted to get key '{}' but the sync connection is not initialized", key);
      return null;
    }

    return this.syncPublisher.get(key);
  }

  /**
   * Sets a key with an expiry time in seconds using the regular (non-pub/sub) connection.
   *
   * @param key        the key to set
   * @param value      the value to store
   * @param ttlSeconds the time-to-live in seconds
   */
  @Override
  public void setWithExpiry(@NotNull String key, @NotNull String value, long ttlSeconds) {
    if (this.syncPublisher == null) {
      LOGGER.warn("Attempted to set key '{}' with expiry but the sync connection is not initialized", key);
      return;
    }

    this.syncPublisher.setex(key, ttlSeconds, value);
  }

  /**
   * Atomically sets the key only if it does not already exist using the regular (non-pub/sub)
   * connection.
   *
   * @param key   the key to set
   * @param value the value to store
   */
  @Override
  public void setIfAbsent(@NotNull String key, @NotNull String value) {
    if (this.syncPublisher == null) {
      LOGGER.warn("Attempted to set-if-absent key '{}' but the sync connection is not initialized", key);
      return;
    }

    this.syncPublisher.setnx(key, value);
  }

  /**
   * Checks whether a key exists in Redis using the regular (non-pub/sub) connection.
   *
   * @param key the key to check
   * @return {@code true} if the key exists, otherwise {@code false}
   */
  @Override
  public boolean existsKey(@NotNull String key) {
    if (this.syncPublisher == null) {
      LOGGER.warn("Attempted to check existence of key '{}' but the sync connection is not initialized", key);
      return false;
    }

    return this.syncPublisher.exists(key) > 0;
  }

  /**
   * Deletes a key from Redis using the regular (non-pub/sub) connection.
   *
   * @param key the key to delete
   */
  @Override
  public void deleteKey(@NotNull String key) {
    if (this.syncPublisher == null) {
      LOGGER.warn("Attempted to delete key '{}' but the sync connection is not initialized", key);
      return;
    }

    this.syncPublisher.del(key);
  }

  /**
   * Represents the Lettuce-provided implementation of the {@link Depot} interface.
   *
   * @param <K> the type of the key in the depot
   * @param <V> the type of the object value in the depot
   */
  public final class LettuceDepot<K, V extends DepotEntry<K, V>> implements Depot<K, V> {

    /**
     * {@link Gson} instance used for serializing and deserializing depot entries.
     */
    private final Gson gson = packetSerializer.gson();

    /**
     * The Redis hash key name used to store entries for this depot.
     */
    private final String name;

    /**
     * The class representing the value type stored in this depot.
     */
    private final Class<V> valueClass;

    /**
     * Constructs a new {@link LettuceDepot} instance.
     *
     * @param valueClass the class of the depot value
     */
    public LettuceDepot(@NotNull Class<V> valueClass) {
      this.name = DEPOT_TEMPLATE.formatted(getNamespace(), VERSION, valueClass.getSimpleName().toLowerCase());
      this.valueClass = valueClass;
    }

    /**
     * Checks whether a value is present in the depot for the given key.
     *
     * @param key the key to look up
     * @return {@code true} if a value exists for the key, otherwise {@code false}
     */
    @Override
    public boolean contains(@NotNull K key) {
      return depotCommands.hexists(this.name, parseKey(key));
    }

    /**
     * Retrieves a value from the depot by key.
     *
     * @param key the key to retrieve the value for
     * @return the deserialized value, or {@code null} if none exists
     */
    @Override
    public @Nullable V get(@NotNull K key) {
      String data = depotCommands.hget(this.name, parseKey(key));
      return data == null ? null : deserialize(data);
    }

    /**
     * Inserts or updates the given value in the depot.
     *
     * @param value the value to upsert
     */
    @Override
    public void upsert(@NotNull V value) {
      depotCommands.hset(this.name, parseKey(value.getUniqueId()), serialize(value));
      value.setDepot(this);
    }

    /**
     * Removes a value from the depot by key, returning the removed value if present.
     *
     * @param key the key whose mapping should be removed
     * @return the removed value, or {@code null} if no value existed
     */
    @Override
    public @Nullable V remove(@NotNull K key) {
      String field = parseKey(key);
      String data = depotCommands.hget(this.name, field);
      if (data == null) {
        return null;
      }

      depotCommands.hdel(this.name, field);
      return deserialize(data);
    }

    /**
     * Returns a collection of all values stored in this depot.
     *
     * @return a collection of all deserialized values
     */
    @Override
    public Collection<V> values() {
      return depotCommands.hvals(this.name).stream()
              .map(this::deserialize)
              .toList();
    }

    /**
     * Returns the number of entries stored in this depot.
     *
     * @return the number of entries in this depot
     */
    @Override
    public int size() {
      return depotCommands.hlen(this.name).intValue();
    }

    /**
     * Returns a collection of all keys stored in this depot.
     *
     * @return a collection of all keys
     */
    @Override
    public Collection<String> keys() {
      return depotCommands.hkeys(this.name);
    }

    /**
     * Serializes the specified entry into a JSON string.
     *
     * @param entry the entry to serialize
     * @return the JSON string representation of the entry
     */
    private @NotNull String serialize(@NotNull V entry) {
      return gson.toJson(entry, this.valueClass);
    }

    /**
     * Deserializes the specified JSON string into an entry and associates it with this depot.
     *
     * @param data the JSON string to deserialize
     * @return the deserialized entry
     */
    private @NotNull V deserialize(@NotNull String data) {
      V entry = gson.fromJson(data, this.valueClass);
      entry.setDepot(this);

      return entry;
    }

    /**
     * Parses the specified key into a string suitable for use in Redis.
     *
     * @param key the key to parse
     * @return the string representation of the key
     */
    private @NotNull String parseKey(K key) {
      return String.valueOf(key);
    }
  }
}
