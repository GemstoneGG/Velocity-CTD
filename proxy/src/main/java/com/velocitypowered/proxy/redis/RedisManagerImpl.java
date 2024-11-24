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
import com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler;
import com.velocitypowered.proxy.redis.multiproxy.RedisGetPlayerPingRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisPlayerSetTransferringRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessage;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessageToUuidRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisServerAlertRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSwitchServerRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisTransferCommandRequest;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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

    registerListeners(velocityServer);
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

    listen(RedisSwitchServerRequest.ID, RedisSwitchServerRequest.class, it -> {
      proxy.getPlayer(it.username()).ifPresent(player -> {
        proxy.getServer(it.server()).ifPresent(server -> {
          player.createConnectionRequest(server).connectWithIndication();
        });
      });
    });

    listen(RedisPlayerSetTransferringRequest.ID, RedisPlayerSetTransferringRequest.class, it -> {
      MultiProxyHandler.RemotePlayerInfo info = proxy.getMultiProxyHandler().getPlayerInfo(it.uuid());
      if (info != null) {
        info.setBeingTransferred(it.transferring());
      }

      if (!it.transferring()) {
        proxy.getMultiProxyHandler().getTransferringServers().remove(it.uuid());
      } else {
        proxy.getMultiProxyHandler().getTransferringServers().put(it.uuid(), it.currentlyConnectedServer());
        proxy.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
          proxy.getMultiProxyHandler().getTransferringServers().remove(it.uuid());
        }).delay(10, TimeUnit.SECONDS).schedule();
      }
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
  }

  /**
   * Adds a proxy ID to the cache.
   *
   * @param id The ID of the proxy.
   */
  public void addProxyId(String id) {
    if (this.jedisPool == null) {
      return;
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      jedis.sadd("PROXY_IDS", id);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Removes a proxy ID from the cache.
   *
   * @param id The ID of the proxy.
   */
  public void removeProxyId(String id) {
    if (this.jedisPool == null) {
      return;
    }

    try (Jedis jedis = this.jedisPool.getResource()) {
      jedis.srem("PROXY_IDS", id);
    } catch (Exception e) {
      e.printStackTrace();
    }
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
      return new ArrayList<>(jedis.smembers("PROXY_IDS").stream().toList());
    } catch (Exception e) {
      e.printStackTrace();
    }

    return new ArrayList<>();
  }

  private void start(final VelocityConfiguration.Redis redisConfig) {
    try {
      JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
          .ssl(redisConfig.isUseSsl())
          .credentials(new DefaultRedisCredentials(redisConfig.getUsername(),
                  redisConfig.getPassword()))
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
