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

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.velocityctd.proxy.redis.depot.Depot;
import com.velocityctd.proxy.redis.depot.DepotEntry;
import com.velocityctd.proxy.redis.packet.RedisPacket;
import com.velocityctd.proxy.redis.packet.serialization.PacketSerializer;
import com.velocityctd.proxy.redis.registration.ConsumerRouteRegistration;
import com.velocityctd.proxy.redis.registration.RouteRegistration;
import com.velocityctd.proxy.redis.transaction.Transaction;
import com.velocityctd.proxy.redis.transaction.TransactionHandler;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
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
   * Logger used for all Lettuce provider operations and diagnostics.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(LettuceProvider.class);

  /**
   * Validates that a Redis host is not a private/internal IP to prevent SSRF attacks.
   * Blocks:
   * - 169.254.x.x (AWS/cloud metadata endpoint)
   * - 10.x.x.x, 172.16-31.x.x, 192.168.x.x (RFC 1918 private ranges)
   * - 127.0.0.1 (IPv4 localhost)
   * - ::1, ::0 (IPv6 localhost)
   * - 0.0.0.0 (any)
   * - 255.255.255.255 (broadcast)
   *
   * @param host the host to validate
   * @throws SecurityException if the host is a private/internal IP
   */
  private static void validateRedisHost(final String host) {
    if (host == null || host.isBlank()) {
      throw new SecurityException("Redis host cannot be null or blank");
    }

    // Check for blocked hostnames/IPs
    final String lowerHost = host.toLowerCase();

    // Block localhost and broadcast
    if (lowerHost.equals("localhost") || lowerHost.equals("127.0.0.1")
        || lowerHost.equals("::1") || lowerHost.equals("::0")
        || lowerHost.equals("0.0.0.0") || lowerHost.equals("255.255.255.255")) {
      throw new SecurityException(
          "Redis host '" + host + "' is blocked (localhost/broadcast address). "
          + "This is a potential SSRF vulnerability.");
    }

    // Parse IP address to check private ranges
    try {
      java.net.InetAddress addr = java.net.InetAddress.getByName(host);

      // Block 169.254.x.x (AWS/cloud metadata)
      if (addr instanceof java.net.Inet4Address inet4) {
        byte[] bytes = inet4.getAddress();
        if (bytes.length == 4) {
          int b0 = bytes[0] & 0xFF;
          int b1 = bytes[1] & 0xFF;

          // AWS metadata: 169.254.169.254 (also 169.254.x.x in general)
          if (b0 == 169 && b1 == 254) {
            throw new SecurityException(
                "Redis host '" + host + "' is in AWS metadata range (169.254.x.x). "
                + "This is a potential SSRF vulnerability.");
          }

          // RFC 1918: 10.0.0.0/8
          if (b0 == 10) {
            throw new SecurityException(
                "Redis host '" + host + "' is in private range (10.x.x.x). "
                + "This is a potential SSRF vulnerability.");
          }

          // RFC 1918: 172.16.0.0/12
          if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            throw new SecurityException(
                "Redis host '" + host + "' is in private range (172.16-31.x.x). "
                + "This is a potential SSRF vulnerability.");
          }

          // RFC 1918: 192.168.0.0/16
          if (b0 == 192 && b1 == 168) {
            throw new SecurityException(
                "Redis host '" + host + "' is in private range (192.168.x.x). "
                + "This is a potential SSRF vulnerability.");
          }
        }
      }
    } catch (java.net.UnknownHostException e) {
      // Hostname resolution failed - that's fine, might be a valid hostname
      // The actual connection will fail later with a proper error
      LOGGER.debug("Could not resolve Redis host for validation: {}", host);
    }
  }

  /**
   * The underlying Lettuce {@link RedisClient} used to establish Redis connections.
   */
  private final RedisClient client;

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
   * Constructs a new {@link LettuceProvider}.
   *
   * @param config the {@link VelocityConfiguration.Redis} instance to use for connection credentials
   */
  public LettuceProvider(final VelocityConfiguration.Redis config) {
    super();

    // Validate the Redis host to prevent SSRF attacks
    validateRedisHost(config.getHost());

    // Build the Redis URI with proper SSL configuration
    final RedisURI.Builder uriBuilder = RedisURI.Builder.redis(config.getHost(), config.getPort())
            .withAuthentication(Objects.requireNonNullElse(config.getUsername(), ""),
                    Objects.requireNonNullElse(config.getPassword(), ""));

    if (config.isUseSsl()) {
      // Enable SSL with JVM's default trust manager for certificate validation
      // The JVM's default SSL context uses the system trust store (cacerts)
      // which validates server certificates by default.
      // For self-signed certificates, add the CA certificate to the JVM trust store:
      //   keytool -import -alias redis-ca -keystore $JAVA_HOME/lib/security/cacerts -file redis-ca.crt
      uriBuilder.withSsl(true);
      LOGGER.info("SSL enabled for Redis connection to {}:{} (using JVM trust store)",
              config.getHost(), config.getPort());
    } else {
      uriBuilder.withSsl(false);
      LOGGER.debug("SSL disabled for Redis connection to {}:{}", config.getHost(), config.getPort());
    }

    this.client = RedisClient.create(uriBuilder.build());
  }

  /**
   * Restarts the underlying Redis Pub/Sub connection and resubscribes to the channel.
   *
   * <p>If an existing publisher connection is present, it is closed before a new
   * connection is created.</p>
   */
  @Override
  public void restart() {
    if (this.publisher != null) {
      this.publisher.getStatefulConnection().close();
    }

    if (this.syncPublisher != null) {
      this.syncPublisher.getStatefulConnection().close();
    }

    final StatefulRedisPubSubConnection<String, String> connection = this.client.connectPubSub();

    // Tracks whether the initial subscribe has completed. The first subscribed() callback is
    // the initial subscribe; every subsequent one is a re-subscribe after a reconnect.
    final AtomicBoolean subscribedOnce = new AtomicBoolean(false);

    connection.addListener(new RedisPubSubAdapter<>() {
      @Override
      public void subscribed(final String channel, final long count) {
        if (CHANNEL.equals(channel) && subscribedOnce.getAndSet(true)) {
          // Re-subscribe after a reconnect, notify listeners so they can reload state.
          fireReconnectListeners();
        }
      }

      @Override
      public void message(final String channel, final String message) {
        if (!channel.equals(CHANNEL)) {
          return;
        }

        final RedisPacket redisPacket = PacketSerializer.deserialize(message);
        if (redisPacket == null) {
          LOGGER.warn("Received a null packet from channel '{}', ignoring", channel);
          return;
        }

        if (redisPacket.isOneWay()) {
          handleOneWay(redisPacket);
          return;
        }

        if (!redisPacket.isReply()) {
          final TransactionHandler<?, ?> transactionHandler = transactionHandlers.get(Preconditions.checkNotNull(
                  redisPacket.getTransactionType(), "transactionType is null"));
          if (transactionHandler == null) {
            return;
          }

          final RedisPacket replyPacket = transactionHandler.getReplyPacket(redisPacket);
          if (replyPacket == null) {
            return;
          }

          LettuceProvider.this.publish(replyPacket);
        } else {
          final UUID transactionId = Preconditions.checkNotNull(redisPacket.getTransactionId());

          final Transaction<?, ?> transaction = PENDING_TRANSACTIONS.remove(transactionId);
          if (transaction == null) {
            return;
          }

          transaction.complete(redisPacket);
        }
      }
    });

    this.publisher = connection.async();
    this.publisher.subscribe(CHANNEL);

    this.syncPublisher = connection.sync();

    LOGGER.info("Connected to Lettuce Redis Server on channel '{}'", CHANNEL);
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

    this.client.shutdown();

    LOGGER.info("Disconnected from Lettuce Redis Server on channel '{}'", CHANNEL);
  }

  /**
   * Publishes the given Redis packet to the configured Redis channel.
   *
   * <p>If the publisher has not been initialized yet, a warning is logged and the packet is not sent.</p>
   *
   * @param packet the packet to publish
   * @param <T> the type of the Redis packet
   */
  @Override
  public <T extends RedisPacket> void publish(final @NotNull T packet) {
    if (this.publisher == null) {
      LOGGER.warn("Attempted to publish a packet to channel '{}' but the publisher is not initialized", CHANNEL);
      return;
    }

    this.publisher.publish(CHANNEL, PacketSerializer.serialize(packet)).whenComplete((received, throwable) -> {
      if (throwable != null) {
        LOGGER.warn("Failed to publish packet to '{}' channel", CHANNEL, throwable);
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
  public <K, V extends DepotEntry<K, V>> @NotNull Depot<K, V> createDepot(final Class<V> valueClass) {
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
   * Handles a one-way {@link RedisPacket} by routing it through a registered {@link RouteRegistration}, if present.
   *
   * @param redisPacket the one-way packet to handle
   * @param <T> the type of Redis packet
   */
  @SuppressWarnings("unchecked")
  private <T extends RedisPacket> void handleOneWay(final @NotNull T redisPacket) {
    final RouteRegistration<T> routeRegistration = (RouteRegistration<T>) routeRegistrations.get(redisPacket.getType());
    if (routeRegistration == null) {
      LOGGER.warn("Received a packet of type '{}' from channel '{}', but no route registration exists, ignoring",
              redisPacket.getType(), CHANNEL);
      return;
    }

    try {
      if (routeRegistration instanceof ConsumerRouteRegistration<T> consumerRegistration) {
        consumerRegistration.getConsumer().accept(redisPacket);
      }
    } catch (Throwable ignored) {
      LOGGER.warn("Failed to handle one way packet of type '{}', ignoring", redisPacket.getType());
    }
  }

  /**
   * Sets a key with an expiry time in seconds using the regular (non-pub/sub) connection.
   *
   * @param key        the key to set
   * @param value      the value to store
   * @param ttlSeconds the time-to-live in seconds
   */
  @Override
  public void setWithExpiry(final @NotNull String key, final @NotNull String value, final long ttlSeconds) {
    if (this.syncPublisher == null) {
      LOGGER.warn("Attempted to set key '{}' with expiry but the sync connection is not initialized", key);
      return;
    }

    this.syncPublisher.setex(key, ttlSeconds, value);
  }

  /**
   * Checks whether a key exists in Redis using the regular (non-pub/sub) connection.
   *
   * @param key the key to check
   * @return {@code true} if the key exists, otherwise {@code false}
   */
  @Override
  public boolean existsKey(final @NotNull String key) {
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
  public void deleteKey(final @NotNull String key) {
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
     * Shared {@link Gson} instance used for serializing and deserializing depot entries.
     */
    private static final Gson GSON = PacketSerializer.GSON;

    /**
     * The Redis hash key name used to store entries for this depot.
     */
    private final String name;

  /**
   * The class representing the value type stored in this depot.
   */
  private final Class<V> valueClass;

  /**
   * Cached TypeToken for efficient Gson deserialization without reflection overhead.
   */
  private final TypeToken<V> typeToken;

  /**
   * The synchronous Pub/Sub commands used to interact with Redis hashes for this depot.
   */
  private final RedisPubSubCommands<String, String> connection;

  /**
   * Constructs a new {@link LettuceDepot} instance.
   *
   * @param valueClass the class of the depot value
   */
  public LettuceDepot(final @NotNull Class<V> valueClass) {
    this.name = valueClass.getSimpleName().toLowerCase();
    this.valueClass = valueClass;
    this.typeToken = TypeToken.get(valueClass);
    this.connection = client.connectPubSub().sync();
  }

    /**
     * Checks whether a value is present in the depot for the given key.
     *
     * @param key the key to look up
     * @return {@code true} if a value exists for the key, otherwise {@code false}
     */
    @Override
    public boolean contains(final @NotNull K key) {
      return this.connection.hexists(this.name, parseKey(key));
    }

    /**
     * Retrieves a value from the depot by key.
     *
     * @param key the key to retrieve the value for
     * @return the deserialized value, or {@code null} if none exists
     */
    @Override
    public @Nullable V get(final @NotNull K key) {
      final String data = this.connection.hget(this.name, parseKey(key));
      return data == null ? null : deserialize(data);
    }

    /**
     * Inserts or updates the given value in the depot.
     *
     * @param value the value to upsert
     */
    @Override
    public void upsert(final @NotNull V value) {
      this.connection.hset(this.name, parseKey(value.getUniqueId()), serialize(value));
      value.setDepot(this);
    }

    /**
     * Removes a value from the depot by key, returning the removed value if present.
     *
     * @param key the key whose mapping should be removed
     * @return the removed value, or {@code null} if no value existed
     */
    @Override
    public @Nullable V remove(final @NotNull K key) {
      if (this.connection.hexists(this.name, parseKey(key))) {
        final String data = this.connection.hget(this.name, parseKey(key));
        this.connection.hdel(this.name, parseKey(key));

        return data == null ? null : deserialize(data);
      }

      return null;
    }

    /**
     * Returns a collection of all values stored in this depot.
     *
     * @return a collection of all deserialized values
     */
    @Override
    public Collection<V> values() {
      return this.connection.hvals(this.name).stream().map(this::deserialize).toList();
    }

    /**
     * Returns a collection of all keys stored in this depot.
     *
     * @return a collection of all keys
     */
    @Override
    public Collection<String> keys() {
      return this.connection.hkeys(this.name);
    }

    /**
     * Serializes the specified entry into a JSON string.
     *
     * @param entry the entry to serialize
     * @return the JSON string representation of the entry
     */
    private @NotNull String serialize(final @NotNull V entry) {
      return GSON.toJson(entry, this.valueClass);
    }

  /**
   * Deserializes the specified JSON string into an entry and associates it with this depot.
   *
   * @param data the JSON string to deserialize
   * @return the deserialized entry
   */
  private @NotNull V deserialize(final @NotNull String data) {
    final V entry = GSON.fromJson(data, this.typeToken);
    entry.setDepot(this);

    return entry;
  }

    /**
     * Parses the specified key into a string suitable for use in Redis.
     *
     * @param key the key to parse
     * @return the string representation of the key
     */
     private @NotNull String parseKey(final K key) {
       return String.valueOf(key);
     }
   }

   /**
    * Returns the synchronous Redis pub/sub command interface used for key operations.
    * This is used for atomic leader election operations in RedisVelocityQueueManager.
    *
    * @return the sync publisher
    */
   public RedisPubSubCommands<String, String> getSyncPublisher() {
     return this.syncPublisher;
   }
}
