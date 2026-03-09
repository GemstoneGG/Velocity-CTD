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

package com.velocitypowered.proxy.redis;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.queue.ServerQueueEntry;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.queue.cache.SerializableQueue;
import com.velocitypowered.proxy.redis.multiproxy.RedisGetPlayerPingRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisKickPlayerRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisPlayerSetTransferringRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessage;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessageToUuidRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisServerAlertRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSwitchServerRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisTransferCommandRequest;
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Redis connectivity and communication within the Velocity proxy using Lettuce.
 *
 * <p>This class sets up a Redis connection using Lettuce and provides methods to send
 * and receive messages through a dedicated Redis channel, enabling multi-proxy
 * communication. It includes configuration management and error handling to
 * ensure reliable operation within the Velocity environment.</p>
 */
public class RedisManagerImpl {

  /**
   * The SLF4J logger instance for logging RedisManager-related events and errors.
   */
  private static final Logger logger = LoggerFactory.getLogger(RedisManagerImpl.class);

  /**
   * The shared Gson instance used for (de)serializing Redis packet payloads and cache entries.
   */
  private static final Gson gson = new Gson();

  /**
   * The Redis pub/sub channel used for inter-proxy communication.
   */
  private static final String CHANNEL = "velocityredis";

  /**
   * The Redis key used to store serialized {@link RemotePlayerInfo} entries.
   */
  private static final String CACHE_KEY = "remote-players";

  /**
   * The Redis key used to store serialized {@link SerializableQueue} entries.
   */
  private static final String QUEUE_CACHE_KEY = "queue-cache";

  /**
   * The Lettuce Redis client used for Redis operations.
   */
  private @MonotonicNonNull RedisClient redisClient;

  /**
   * The main Redis connection for synchronous operations.
   */
  private @MonotonicNonNull StatefulRedisConnection<String, String> connection;

  /**
   * The Redis pub/sub connection for asynchronous message handling.
   */
  private @MonotonicNonNull StatefulRedisPubSubConnection<String, String> pubSubConnection;

  /**
   * The {@link VelocityPubSub} handler used for subscribing to and dispatching Redis channel messages.
   */
  private final VelocityPubSub pubSub;

  /**
   * Scheduled executor for background tasks.
   */
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

  /**
   * Constructs a Redis manager using the given Velocity server instance to retrieve
   * configuration and initialize the Redis connection if enabled.
   *
   * @param velocityServer the instance of the Velocity server
   */
  public RedisManagerImpl(final VelocityServer velocityServer) {
    VelocityConfiguration.Redis redisConfig = velocityServer.getConfiguration().getRedis();
    this.pubSub = new VelocityPubSub();

    if (redisConfig.isEnabled()) {
      this.start(redisConfig, velocityServer);
    }

    registerListeners(velocityServer);
  }

  private void startKeepalive(final String proxyId, final VelocityServer server) {
    if (connection == null) {
      return;
    }

    scheduler.scheduleAtFixedRate(() -> {
      if (server.isStartedShutdown()) {
        return;
      }

      // Use async operations to prevent blocking
      CompletableFuture.runAsync(() -> {
        try {
          RedisAsyncCommands<String, String> commands = connection.async();
          commands.setex("PROXY_HEARTBEAT:" + proxyId, 30, "online")
              .thenAccept(result -> {
                // Success - no action needed
              })
              .exceptionally(throwable -> {
                logger.error("Keepalive failed for Proxy ID '{}'.", proxyId, throwable);
                return null;
              });
        } catch (Exception e) {
          logger.error("Keepalive failed for Proxy ID '{}'.", proxyId, e);
        }
      });
    }, 0, 30, TimeUnit.SECONDS);
  }

  private void registerListeners(final VelocityServer proxy) {
    listen(RedisServerAlertRequest.ID, RedisServerAlertRequest.class, it -> {
      Component component = it.component();

      if (component != null) {
        proxy.sendMessage(component);
      }
    });

    listen(RedisGetPlayerPingRequest.ID, RedisGetPlayerPingRequest.class, it ->
        proxy.getPlayer(it.playerToCheck()).ifPresent(player -> {
          Component component = Component.translatable("velocity.command.ping.other", NamedTextColor.GREEN)
              .arguments(
                  Argument.string("player", player.getUsername()),
                  Argument.numeric("ping", player.getPing()));

          send(new RedisSendMessage(it.commandSender(), component));
        }));

    listen(RedisTransferCommandRequest.ID, RedisTransferCommandRequest.class, it -> {
      ConnectedPlayer connectedPlayer = (ConnectedPlayer) proxy.getPlayer(it.player()).orElse(null);
      if (connectedPlayer == null) {
        return;
      }

      if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
        String connectedServer = connectedPlayer.getConnectedServer() != null ? connectedPlayer.getConnectedServer().getServerInfo().getName() : null;
        send(new RedisPlayerSetTransferringRequest(connectedPlayer.getUniqueId(), true, connectedServer));
      }

      proxy.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
          connectedPlayer.transferToHost(new InetSocketAddress(it.ip(), it.port()));
        } else {
          send(new RedisSendMessageToUuidRequest(it.requester(),
              Component.translatable("velocity.command.transfer.invalid-version")
                  .arguments(Argument.string("player", connectedPlayer.getUsername()))));
        }
      }).delay(1, TimeUnit.SECONDS).schedule();
    });

    listen(RedisSwitchServerRequest.ID, RedisSwitchServerRequest.class, it ->
            proxy.getPlayer(it.username()).ifPresent(player ->
                    proxy.getServer(it.server()).ifPresent(server ->
                            player.createConnectionRequest(server).connectWithIndication())));

    listen(RedisKickPlayerRequest.ID, RedisKickPlayerRequest.class, it -> {
      if (proxy.getMultiProxyHandler().getOwnProxyId().equalsIgnoreCase(it.proxyId())) {
        return;
      }

      ConnectedPlayer player = (ConnectedPlayer) proxy.getPlayer(it.player()).orElse(null);
      if (player != null) {
        player.setDontRemoveFromRedis(true);
        player.disconnect0(Component.translatable("velocity.error.already-connected-proxy.remote"), true);
      }
    });
  }

  /**
   * Add paused queue.
   *
   * @param serverName The name of the server.
   */
  public void addPausedQueue(final String serverName) {
    if (this.connection == null) {
      return;
    }

    // Use async operations to prevent blocking
    CompletableFuture.runAsync(() -> {
      try {
        RedisAsyncCommands<String, String> commands = connection.async();
        commands.sadd("PAUSED_QUEUES", serverName)
            .thenAccept(result -> {
              // Success - no action needed
            })
            .exceptionally(throwable -> {
              logger.error("Failed to add paused queue: {}", serverName, throwable);
              return null;
            });
      } catch (Exception e) {
        logger.error("Failed to add paused queue: {}", serverName, e);
      }
    });
  }

  /**
   * Remove paused queue.
   *
   * @param serverName The name of the server.
   */
  public void removePausedQueue(final String serverName) {
    if (this.connection == null) {
      return;
    }

    // Use async operations to prevent blocking
    CompletableFuture.runAsync(() -> {
      try {
        RedisAsyncCommands<String, String> commands = connection.async();
        commands.srem("PAUSED_QUEUES", serverName)
            .thenAccept(result -> {
              // Success - no action needed
            })
            .exceptionally(throwable -> {
              logger.error("Failed to remove paused queue: {}", serverName, throwable);
              return null;
            });
      } catch (Exception e) {
        logger.error("Failed to remove paused queue: {}", serverName, e);
      }
    });
  }

  /**
   * Get all the paused queues.
   *
   * @return All the paused queues.
   */
  public List<String> getPausedQueues() {
    if (this.connection == null) {
      return new ArrayList<>();
    }

    try {
      RedisCommands<String, String> commands = connection.sync();
      return new ArrayList<>(commands.smembers("PAUSED_QUEUES"));
    } catch (Exception e) {
      logger.error("Failed to get paused queues", e);
      return new ArrayList<>();
    }
  }

  /**
   * Gets all proxy ids from the cache.
   *
   * @return all the proxy ids.
   */
  public List<String> getProxyIds() {
    if (this.connection == null) {
      return new ArrayList<>();
    }

    try {
      RedisCommands<String, String> commands = connection.sync();
      return commands.keys("PROXY_HEARTBEAT:*").stream()
          .map(key -> key.replace("PROXY_HEARTBEAT:", ""))
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("Failed to get proxy IDs", e);
      return new ArrayList<>();
    }
  }

  /**
   * Remove the proxy ID from the redis cache.
   *
   * @param proxyId The proxy ID.
   */
  public void removeProxyId(final String proxyId) {
    if (connection == null) {
      return;
    }

    // Use async operations to prevent blocking
    CompletableFuture.runAsync(() -> {
      try {
        RedisAsyncCommands<String, String> commands = connection.async();
        commands.del("PROXY_HEARTBEAT:" + proxyId)
            .thenAccept(result -> {
              // Success - no action needed
            })
            .exceptionally(throwable -> {
              logger.error("Failed to remove proxy ID: {}", proxyId, throwable);
              return null;
            });
      } catch (Exception e) {
        logger.error("Failed to remove proxy ID: {}", proxyId, e);
      }
    });
  }

  /**
   * Add a player to the cache.
   *
   * @param player The player to update.
   */
  public void addOrUpdatePlayer(final RemotePlayerInfo player) {
    if (connection == null) {
      return;
    }

    String json = gson.toJson(player);

    // Use async operations to prevent blocking
    CompletableFuture.runAsync(() -> {
      try {
        RedisAsyncCommands<String, String> commands = connection.async();
        commands.hset(CACHE_KEY, player.getUuid().toString(), json)
            .thenAccept(result -> {
              // Success - no action needed
            })
            .exceptionally(throwable -> {
              logger.error("Failed to add/update player: {}", player.getUuid(), throwable);
              return null;
            });
      } catch (Exception e) {
        logger.error("Failed to add/update player: {}", player.getUuid(), e);
      }
    });
  }

  /**
   * Remove a player from the cache.
   *
   * @param info The player to update.
   */
  public void removePlayer(final RemotePlayerInfo info) {
    if (connection == null) {
      return;
    }

    // Use async operations to prevent blocking
    CompletableFuture.runAsync(() -> {
      try {
        RedisAsyncCommands<String, String> commands = connection.async();
        commands.hdel(CACHE_KEY, info.getUuid().toString())
            .thenAccept(result -> {
              // Success - no action needed
            })
            .exceptionally(throwable -> {
              logger.error("Failed to remove player: {}", info.getUuid(), throwable);
              return null;
            });
      } catch (Exception e) {
        logger.error("Failed to remove player: {}", info.getUuid(), e);
      }
    });
  }

  /**
   * Checks if a player exists in the Redis cache.
   *
   * @param uniqueId the players UUID.
   *
   * @return whether the player exists or not.
   */
  public boolean containsPlayer(final UUID uniqueId) {
    if (connection == null) {
      return false;
    }

    try {
      RedisCommands<String, String> commands = connection.sync();
      return commands.hexists(CACHE_KEY, uniqueId.toString());
    } catch (Exception e) {
      logger.error("Failed to check if player exists: {}", uniqueId, e);
      return false;
    }
  }

  /**
   * Get the cache of players.
   *
   * @return the list of players.
   */
  public List<RemotePlayerInfo> getCache() {
    if (connection == null) {
      return new ArrayList<>();
    }

    try {
      RedisCommands<String, String> commands = connection.sync();
      Map<String, String> playerMap = commands.hgetall(CACHE_KEY);
      return playerMap.values().stream()
          .map(json -> gson.fromJson(json, RemotePlayerInfo.class))
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("Failed to get player cache", e);
      return new ArrayList<>();
    }
  }

  /**
   * Add or update a queue in the cache.
   *
   * @param queue The queue to add or update.
   */
  public void addOrUpdateQueue(final ServerQueueStatus queue) {
    if (this.connection == null) {
      return;
    }

    // Use async operations to prevent blocking
    CompletableFuture.runAsync(() -> {
      try {
        RedisAsyncCommands<String, String> commands = connection.async();
        commands.hset(QUEUE_CACHE_KEY, queue.getServerName(), gson.toJson(new SerializableQueue(queue)))
            .thenAccept(result -> {
              // Success - no action needed
            })
            .exceptionally(throwable -> {
              logger.error("Failed to add/update queue: {}", queue.getServerName(), throwable);
              return null;
            });
      } catch (Exception e) {
        logger.error("Failed to add/update queue: {}", queue.getServerName(), e);
      }
    });
  }

  /**
   * Updates the entry.
   *
   * @param serverQueueEntry The entry to update.
   */
  public void addOrUpdateEntry(final ServerQueueEntry serverQueueEntry) {
    if (this.connection == null) {
      return;
    }

    ServerQueueStatus status = getQueue(serverQueueEntry.getTarget().getServerInfo().getName())
        .convert(serverQueueEntry.getProxy(), serverQueueEntry.getTarget());
    if (status == null) {
      return;
    }

    ServerQueueEntry entry = status.getEntry(serverQueueEntry.getPlayer()).orElse(null);
    if (entry == null) {
      return;
    }

    entry.update(serverQueueEntry.getConnectionAttempts(), serverQueueEntry.isWaitingForConnection(),
        serverQueueEntry.getPriority(),
        serverQueueEntry.isFullBypass(),
        serverQueueEntry.isQueueBypass());

    addOrUpdateQueue(status);
  }

  /**
   * Get a queue from the cache based on username.
   *
   * @param serverName The name of the server.
   * @return The queue from the cache.
   */
  public SerializableQueue getQueue(final String serverName) {
    if (this.connection == null) {
      return null;
    }

    try {
      RedisCommands<String, String> commands = connection.sync();
      String json = commands.hget(QUEUE_CACHE_KEY, serverName);
      if (json == null) {
        return null; // Key does not exist
      }
      return gson.fromJson(json, SerializableQueue.class);
    } catch (Exception e) {
      logger.error("Failed to get queue: {}", serverName, e);
      return null; // Return null in case of an error
    }
  }

  /**
   * Get all the queues from the cache.
   *
   * @return All the queues from the cache.
   */
  public List<SerializableQueue> getAllQueues() {
    if (this.connection == null) {
      return new ArrayList<>();
    }

    try {
      RedisCommands<String, String> commands = connection.sync();
      Map<String, String> queueMap = commands.hgetall(QUEUE_CACHE_KEY);
      return queueMap.values().stream()
          .map(json -> gson.fromJson(json, SerializableQueue.class))
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("Failed to get all queues", e);
      return new ArrayList<>();
    }
  }

  private void start(final VelocityConfiguration.Redis redisConfig, final VelocityServer server) {
    try {
      // Build Redis URI with optimized settings
      RedisURI.Builder uriBuilder = RedisURI.builder()
          .withHost(redisConfig.getHost())
          .withPort(redisConfig.getPort())
          .withTimeout(Duration.ofSeconds(60));

      if (redisConfig.isUseSsl()) {
        uriBuilder.withSsl(true);
      }

      if (redisConfig.getUsername() != null && !redisConfig.getUsername().isEmpty()) {
        uriBuilder.withAuthentication(redisConfig.getUsername(),
            redisConfig.getPassword().equalsIgnoreCase("") ? null : redisConfig.getPassword());
      } else if (!redisConfig.getPassword().equalsIgnoreCase("")) {
        uriBuilder.withPassword(redisConfig.getPassword());
      }

      RedisURI redisUri = uriBuilder.build();

      // Create Redis client with connection pooling
      this.redisClient = RedisClient.create(redisUri);

      // Create main connection
      this.connection = redisClient.connect();

      // Create pub/sub connection
      this.pubSubConnection = redisClient.connectPubSub();
      this.pubSubConnection.addListener(this.pubSub);

      // Subscribe to the channel
      RedisPubSubCommands<String, String> pubSubCommands = pubSubConnection.sync();
      pubSubCommands.subscribe(CHANNEL);

      validateProxyId(redisConfig.getProxyId());
      startKeepalive(redisConfig.getProxyId(), server);
      startKeepalivePlayers(server);
    } catch (Exception e) {
      logger.error("Failed to setup Redis connection", e);
    }
  }

  private void startKeepalivePlayers(final VelocityServer proxy) {
    proxy.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
      for (RemotePlayerInfo info : this.getCache()) {
        if (info.getProxyId().equalsIgnoreCase(proxy.getConfiguration().getRedis().getProxyId())) {
          if (proxy.getPlayer(info.getUuid()).isEmpty()) {
            removePlayer(info);
          }
        }
      }
    }).repeat(30, TimeUnit.SECONDS).schedule();
  }

  private void validateProxyId(final String proxyId) {
    if (connection == null) {
      throw new IllegalStateException("Redis connection is not initialized.");
    }

    try {
      RedisCommands<String, String> commands = connection.sync();
      if (commands.exists("PROXY_HEARTBEAT:" + proxyId) > 0) {
        logger.error("Proxy ID '{}' is still marked as running. Killing"
            + " your proxies with Redis enabled is not suggested. Please wait"
            + " for Redis to automatically determine whether the proxy is online or not.", proxyId);
        System.exit(1);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to validate Proxy ID.", e);
    }
  }

  /**
   * Sends an object on the given channel.
   *
   * @param packet the object to send
   */
  public void send(final RedisPacket packet) {
    if (this.connection == null) {
      return;
    }

    // Use async operations to prevent blocking
    CompletableFuture.runAsync(() -> {
      try {
        JsonElement packetData = gson.toJsonTree(packet);
        JsonObject object = new JsonObject();
        object.add("obj", packetData);
        object.addProperty("id", packet.getId());

        RedisAsyncCommands<String, String> commands = connection.async();
        commands.publish(CHANNEL, gson.toJson(object))
            .thenAccept(result -> {
              // Success - no action needed
            })
            .exceptionally(throwable -> {
              logger.error("Failed to send Redis pubsub message", throwable);
              return null;
            });
      } catch (Exception e) {
        logger.error("Failed to send Redis pubsub message", e);
      }
    });
  }

  /**
   * Listens to a channel.
   *
   * @param id the packet ID to listen for
   * @param clazz the packet class for the messages
   * @param consumer the handler to call
   * @param <T> the type of the message
   */
  public <T> void listen(final String id, final Class<T> clazz, final Consumer<T> consumer) {
    if (this.connection == null) {
      return;
    }

    this.pubSub.register(id, clazz, consumer);
  }

  /**
   * Checks whether Redis is currently enabled and the connection is active.
   *
   * @return {@code true} if Redis is enabled and initialized, {@code false} otherwise
   */
  public boolean isEnabled() {
    return redisClient != null && connection != null && connection.isOpen();
  }

  /**
   * Closes all Redis connections and shuts down the scheduler.
   */
  public void shutdown() {
    if (pubSubConnection != null && pubSubConnection.isOpen()) {
      pubSubConnection.close();
    }

    if (connection != null && connection.isOpen()) {
      connection.close();
    }

    if (redisClient != null) {
      redisClient.shutdown();
    }

    scheduler.shutdown();
  }

  /**
   * Manages subscriptions and incoming message handling on a Redis channel.
   *
   * <p>This inner class extends {@link RedisPubSubAdapter} to implement a custom message
   * handler that dispatches messages based on packet ID to registered listeners.</p>
   */
  public static class VelocityPubSub extends RedisPubSubAdapter<String, String> {

    /**
     * The SLF4J logger instance for logging Redis pub/sub events and errors.
     */
    private static final Logger logger = LoggerFactory.getLogger(VelocityPubSub.class);

    /**
     * A map of packet ID strings to their corresponding message listeners.
     *
     * <p>This is used to dispatch Redis messages based on the packet ID field.</p>
     */
    private final Map<String, ChannelRegistration<?>> listeners = new ConcurrentHashMap<>();

    /**
     * Handles an incoming Redis pub/sub message.
     *
     * <p>The message is deserialized into a JSON object, the packet ID is extracted,
     * and the appropriate {@link ChannelRegistration} is looked up. If a matching
     * registration exists, the message is dispatched via {@link #onMessage0(ChannelRegistration, String, JsonObject)}.</p>
     *
     * @param channel the Redis channel the message was published on
     * @param message the raw JSON message payload
     */
    @Override
    public void message(final String channel, final String message) {
      try {
        JsonObject obj = gson.fromJson(message, JsonObject.class);
        String packetId = obj.getAsJsonPrimitive("id").getAsString();
        JsonObject packetObj = obj.getAsJsonObject("obj");
        ChannelRegistration<?> registration = this.listeners.get(packetId);

        if (registration == null) {
          return;
        }

        this.onMessage0(registration, channel, packetObj);
      } catch (Exception e) {
        logger.error("Error processing Redis message on channel: {}", channel, e);
      }
    }

    // second function for `T` parameter
    private <T> void onMessage0(final ChannelRegistration<T> registration, final String channel,
                                final JsonObject obj) {
      T instance;

      try {
        instance = gson.fromJson(obj, registration.clazz);
      } catch (JsonSyntaxException e) {
        logger.error("received invalid JSON on channel {} for packet class {}", channel,
            registration.clazz, e);
        return;
      }

      try {
        registration.consumer.accept(instance);
      } catch (Throwable th) {
        logger.error("packet handler for packet class {} threw", registration.clazz, th);
      }
    }

    private record ChannelRegistration<T>(Class<T> clazz, Consumer<T> consumer) {
    }

    private <T> void register(final String id, final Class<T> clazz, final Consumer<T> consumer) {
      this.listeners.put(id, new ChannelRegistration<>(clazz, consumer));
    }
  }
}
