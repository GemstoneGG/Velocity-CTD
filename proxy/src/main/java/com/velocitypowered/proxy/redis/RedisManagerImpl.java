package com.velocitypowered.proxy.redis;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RedisManagerImpl {
  private static final String CHANNEL = "velocityredis";

  private static final Logger logger = LoggerFactory.getLogger(RedisManagerImpl.class);
  private static final Gson gson = new Gson();

  private @MonotonicNonNull JedisPool jedisPool;
  private final VelocityPubSub pubSub;

  public RedisManagerImpl(VelocityServer velocityServer) {
    VelocityConfiguration.Redis redisConfig = velocityServer.getConfiguration().getRedis();
    this.pubSub = new VelocityPubSub();

    if (redisConfig.isEnabled()) {
      this.start(redisConfig);
    }
  }

  private void start(VelocityConfiguration.Redis redisConfig) {
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
   * @param packet the object to send
   */
  public void send(RedisPacket packet) {
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
   * @param id the packet ID to listen for
   * @param clazz the packet class for the messages
   * @param consumer the handler to call
   * @param <T> the type of the message
   */
  public <T> void listen(String id, Class<T> clazz, Consumer<T> consumer) {
    if (this.jedisPool == null) {
      return;
    }

    this.pubSub.register(id, clazz, consumer);
  }

  public static class VelocityPubSub extends JedisPubSub {
    private static final Logger logger = LoggerFactory.getLogger(VelocityPubSub.class);
    private final Map<String, ChannelRegistration<?>> listeners = new HashMap<>();

    @Override
    public void onMessage(String channel, String message) {
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
    private <T> void onMessage0(ChannelRegistration<T> registration, String channel, JsonObject obj) {
      T instance;

      try {
        instance = gson.fromJson(obj, registration.clazz);
      } catch (JsonSyntaxException e) {
        logger.error("Received invalid JSON on channel {} for packet clazz {}", channel, registration.clazz, e);
        return;
      }

      registration.consumer.accept(instance);
    }

    private record ChannelRegistration<T>(Class<T> clazz, Consumer<T> consumer) {}

    private <T> void register(String id, Class<T> clazz, Consumer<T> consumer) {
      this.listeners.put(id, new ChannelRegistration<>(clazz, consumer));
    }
  }
}
