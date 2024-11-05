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
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
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
import redis.clients.jedis.exceptions.JedisException;

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

  private static final Logger logger = LoggerFactory.getLogger(RedisManagerImpl.class);
  private static final Gson gson = new Gson();

  private @MonotonicNonNull JedisPool jedisPool;
  private final VelocityPubSub pubSub;

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
      this.start(redisConfig);
    }
  }

  private void start(final VelocityConfiguration.Redis redisConfig) {
    try {
      JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
          .ssl(redisConfig.isUseSsl())
          .credentials(new DefaultRedisCredentials(redisConfig.getUsername(), redisConfig.getPassword()))
          .build();

      HostAndPort hostAndPort = new HostAndPort(redisConfig.getHost(), redisConfig.getPort());
      JedisPoolConfig poolConfig = new JedisPoolConfig();
      poolConfig.setMaxTotal(redisConfig.getMaxConcurrentConnections());
      poolConfig.setBlockWhenExhausted(false);
      this.jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);

      Thread thread = new Thread(() -> {
        try (Jedis jedis = this.jedisPool.getResource()) {
          jedis.subscribe(this.pubSub, CHANNEL);
        } catch (JedisException e) {
          logger.error("error in pubsub listener", e);
        }
      });
      thread.setName("Velocity Redis PubSub Listener Thread");
      thread.setDaemon(true);
      thread.start();
    } catch (Exception e) {
      logger.error("Failed to set up Redis connection", e);
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
      JsonObject obj = gson.fromJson(message, JsonObject.class);
      String packetId = obj.getAsJsonPrimitive("id").getAsString();
      JsonObject packetObj = obj.getAsJsonObject("obj");
      ChannelRegistration<?> registration = this.listeners.get(packetId);

      if (registration == null) {
        return;
      }

      this.onMessage0(registration, channel, packetObj);
    }

    // second function for `T` parameter
    private <T> void onMessage0(final ChannelRegistration<T> registration, final String channel, final JsonObject obj) {
      T instance;

      try {
        instance = gson.fromJson(obj, registration.clazz);
      } catch (JsonSyntaxException e) {
        logger.error("received invalid JSON on channel {} for packet class {}", channel, registration.clazz, e);
        return;
      }

      try {
        registration.consumer.accept(instance);
      } catch (Throwable th) {
        logger.error("packet handler for packet class {} threw", registration.clazz, th);
      }
    }

    private record ChannelRegistration<T>(Class<T> clazz, Consumer<T> consumer) {}

    private <T> void register(final String id, final Class<T> clazz, final Consumer<T> consumer) {
      this.listeners.put(id, new ChannelRegistration<>(clazz, consumer));
    }
  }
}
