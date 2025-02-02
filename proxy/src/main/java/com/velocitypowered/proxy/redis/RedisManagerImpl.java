/*
 * Copyright (C) 2024 Velocity Contributors
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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.DefaultRedisCredentials;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Manages Redis connectivity and communication within the Velocity proxy.
 *
 * <p>This class sets up a Redis connection pool and provides methods to send
 * and receive messages through a dedicated Redis channel, enabling multi-proxy
 * communication. It includes configuration management and error handling to
 * ensure reliable operation within the Velocity environment.</p>
 */
public class RedisManagerImpl {
  private static final String CHANNEL = "velocityredis";
  private static final String CACHE_KEY = "remote-players";
  private static final String QUEUE_CACHE_KEY = "queue-cache";

  private static final Logger logger = LoggerFactory.getLogger(RedisManagerImpl.class);
  private static final Gson gson = new Gson();

  private @MonotonicNonNull JedisPool jedisPool;
  private final VelocityPubSub pubSub;
  private final AsyncPlayerCache asyncPlayerCache;

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

    int threadPoolSize = Math.max(1, Runtime.getRuntime().availableProcessors() / 8);
    asyncPlayerCache = new AsyncPlayerCache(CACHE_KEY, jedisPool, gson, threadPoolSize);

    registerListeners(velocityServer);
  }

  private void startKeepalive(final String proxyId, final VelocityServer server) {
    if (jedisPool == null) {
      return;
    }

    server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
          if (server.isStartedShutdown()) {
            return;
          }

          try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex("PROXY_HEARTBEAT:" + proxyId, 30, "online");
          } catch (Exception e) {
            logger.error("Keepalive failed for Proxy ID '{}'.", proxyId, e);
          }
        })
        .repeat(30, TimeUnit.SECONDS)
        .schedule();
  }

  private void registerListeners(final VelocityServer proxy) {
    listen(RedisServerAlertRequest.ID, RedisServerAlertRequest.class, it -> {
      Component component = it.component();

      if (component != null) {
        proxy.sendMessage(component);
      }
    });

    listen(RedisGetPlayerPingRequest.ID, RedisGetPlayerPingRequest.class, it -> {
      proxy.getPlayer(it.playerToCheck()).ifPresent(player -> {
        Component component = Component.translatable("velocity.command.ping.other",
            NamedTextColor.GREEN)
               .arguments(Component.text(player.getUsername()),
                   Component.text(player.getPing()));

        send(new RedisSendMessage(it.commandSender(), component));
      });
    });

    listen(RedisTransferCommandRequest.ID, RedisTransferCommandRequest.class, it -> {
      ConnectedPlayer connectedPlayer = (ConnectedPlayer) proxy.getPlayer(it.player()).orElse(null);
      if (connectedPlayer == null) {
        return;
      }

      if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
        String connectedServer = connectedPlayer.getConnectedServer() != null ? connectedPlayer.getConnectedServer().getServerInfo().getName() : null;
        send(new RedisPlayerSetTransferringRequest(connectedPlayer.getUniqueId(), true,
            connectedServer));
      }

      proxy.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
          connectedPlayer.transferToHost(new InetSocketAddress(it.ip(), it.port()));
        } else {
          send(new RedisSendMessageToUuidRequest(it.requester(), Component.translatable("velocity.command.transfer.invalid-version")
              .arguments(Component.text(connectedPlayer.getUsername()))));
        }
      }).delay(1, TimeUnit.SECONDS).schedule();
    });

    listen(RedisSwitchServerRequest.ID, RedisSwitchServerRequest.class, it
        -> proxy.getPlayer(it.username()).ifPresent(player
            -> proxy.getServer(it.server()).ifPresent(server
                -> player.createConnectionRequest(server).connectWithIndication())));

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
    if (this.jedisPool == null) {
      return;
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      jedis.sadd("PAUSED_QUEUES", serverName);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Remove paused queue.
   *
   * @param serverName The name of the server.
   */
  public void removePausedQueue(final String serverName) {
    if (this.jedisPool == null) {
      return;
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      jedis.srem("PAUSED_QUEUES", serverName);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Get all the paused queues.
   *
   * @return All the paused queues.
   */
  public List<String> getPausedQueues() {
    if (this.jedisPool == null) {
      return new ArrayList<>();
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      return new ArrayList<>(jedis.smembers("PAUSED_QUEUES").stream().toList());
    } catch (Exception e) {
      e.printStackTrace();
    }

    return new ArrayList<>();
  }

  /**
   * Gets all proxy ids from the cache.
   *
   * @return all the proxy ids.
   */
  public List<String> getProxyIds() {
    if (this.jedisPool == null) {
      return new ArrayList<>();
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      return new ArrayList<>(jedis.keys("PROXY_HEARTBEAT:*").stream().map(key -> key.replace("PROXY_HEARTBEAT:", "")).collect(Collectors.toList()));
    } catch (Exception e) {
      e.printStackTrace();
    }

    return new ArrayList<>();
  }

  /**
   * Remove the proxy ID from the redis cache.
   *
   * @param proxyId The proxy ID.
   */
  public void removeProxyId(final String proxyId) {
    try (Jedis jedis = this.jedisPool.getResource()) {
      jedis.del("PROXY_HEARTBEAT:" + proxyId);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Add a player to the cache.
   *
   * @param player The player to update.
   */
  public void addOrUpdatePlayer(final RemotePlayerInfo player) {
    if (this.jedisPool == null) {
      return;
    }

    asyncPlayerCache.addOrUpdatePlayer(player);
  }

  /**
   * Remove a player from the cache.
   *
   * @param info The player to update.
   */
  public void removePlayer(final RemotePlayerInfo info) {
    if (this.jedisPool == null) {
      return;
    }
    asyncPlayerCache.removePlayer(info);
  }

  /**
   * Get the cache of players.
   *
   * @return the list of players.
   */
  public List<RemotePlayerInfo> getCache() {
    if (this.jedisPool == null) {
      return new ArrayList<>();
    }
    return asyncPlayerCache.getCache();
  }

  /**
   * Add or update a queue in the cache.
   *
   * @param queue The queue to add or update.
   */
  public void addOrUpdateQueue(final ServerQueueStatus queue) {
    if (this.jedisPool == null) {
      return;
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      jedis.hset(QUEUE_CACHE_KEY, queue.getServerName(), gson.toJson(new SerializableQueue(queue)));
    } catch (JedisDataException ignored) {
      // Ignore raw hash due to redundant logging.
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Updates the entry.
   *
   * @param serverQueueEntry The entry to update.
   */
  public void addOrUpdateEntry(final ServerQueueEntry serverQueueEntry) {
    if (this.jedisPool == null) {
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
    if (this.jedisPool == null) {
      return null;
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      String json = jedis.hget(QUEUE_CACHE_KEY, serverName);
      if (json == null) {
        return null; // Key does not exist
      }
      return gson.fromJson(json, SerializableQueue.class);
    } catch (Exception e) {
      e.printStackTrace();
      return null; // Return null in case of an error
    }
  }

  /**
   * Get all the queues from the cache.
   *
   * @return All the queues from the cache.
   */
  public List<SerializableQueue> getAllQueues() {
    if (this.jedisPool == null) {
      return new ArrayList<>();
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      Map<String, String> queueMap = jedis.hgetAll(QUEUE_CACHE_KEY);
      return queueMap.values().stream()
          .map(json -> gson.fromJson(json, SerializableQueue.class))
          .collect(Collectors.toList());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private void start(final VelocityConfiguration.Redis redisConfig, final VelocityServer server) {
    try {
      JedisPoolConfig poolConfig = new JedisPoolConfig();
      poolConfig.setMaxTotal(redisConfig.getMaxConcurrentConnections());
      poolConfig.setBlockWhenExhausted(false);
      poolConfig.setTestOnBorrow(true);
      poolConfig.setTestOnReturn(true);
      poolConfig.setTestWhileIdle(true);
      poolConfig.setMinEvictableIdleTimeMillis(30000); // 30 seconds before removing stale connections
      poolConfig.setTimeBetweenEvictionRunsMillis(15000); // Check stale connections every 15 sec
      poolConfig.setNumTestsPerEvictionRun(-1); // Test all idle connections
      
      JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
          .ssl(redisConfig.isUseSsl())
          .credentials(new DefaultRedisCredentials(redisConfig.getUsername(),
              redisConfig.getPassword().equalsIgnoreCase("") ? null : redisConfig.getPassword()))
          .build();
      HostAndPort hostAndPort = new HostAndPort(redisConfig.getHost(), redisConfig.getPort());
      this.jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);

      Thread thread = new Thread(() -> {
        try (Jedis jedis = this.jedisPool.getResource()) {
          jedis.subscribe(this.pubSub, CHANNEL);
        } catch (Exception e) {
          logger.error("Error in pubsub listener", e);
        }
      });
      thread.setName("Velocity Redis PubSub Listener Thread");
      thread.setDaemon(true);
      thread.start();

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
    if (jedisPool == null) {
      throw new IllegalStateException("Redis connection pool is not initialized.");
    }

    try (Jedis jedis = jedisPool.getResource()) {
      if (jedis.exists("PROXY_HEARTBEAT:" + proxyId)) {
        logger.error("Proxy ID '{}' is still marked as running. Killing"
            + " your proxies with Redis enabled is not suggested. Please wait"
            + " for Redis to automatically determine whether the proxy is online or not.", proxyId);
        System.exit(0);
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
    if (this.jedisPool == null) {
      return;
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      JsonElement packetData = gson.toJsonTree(packet);
      JsonObject object = new JsonObject();
      object.add("obj", packetData);
      object.addProperty("id", packet.getId());
      jedis.publish(CHANNEL, gson.toJson(object));
    } catch (Exception e) {
      logger.error("Failed to send Redis pubsub message", e);
    }
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
    if (this.jedisPool == null) {
      return;
    }

    this.pubSub.register(id, clazz, consumer);
  }

  public boolean isEnabled() {
    return jedisPool != null;
  }

  /**
   * Manages subscriptions and incoming message handling on a Redis channel.
   *
   * <p>This inner class extends {@link JedisPubSub} to implement a custom message
   * handler that dispatches messages based on packet ID to registered listeners.</p>
   */
  public static class VelocityPubSub extends JedisPubSub {
    private static final Logger logger = LoggerFactory.getLogger(VelocityPubSub.class);
    private final Map<String, ChannelRegistration<?>> listeners = new HashMap<>();

    @Override
    public void onMessage(final String channel, final String message) {
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
        logger.error(ANSI_WHITE + "------------------------------------");
        logger.error(ANSI_RED + "AN ERROR HAS OCCURRED!!!!");
        logger.error(" ");
        logger.error(ANSI_BLUE + "CAUSE OF ERROR: " + ANSI_RESET + "{}", String.valueOf(e.getCause()));
        logger.error(ANSI_YELLOW + "MESSAGE: " + ANSI_RESET + "{}", e.getMessage());
        logger.error(ANSI_WHITE + "------------------------------------");
        logger.error(ANSI_PURPLE + "STACK TRACE:");
        e.printStackTrace();
      }
    }

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_WHITE = "\u001B[37m";

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
