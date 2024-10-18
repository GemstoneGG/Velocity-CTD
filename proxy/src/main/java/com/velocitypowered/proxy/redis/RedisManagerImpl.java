/*
 * Copyright (C) 2018-2024 Velocity Contributors
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
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.redis.RedisManager;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.pubsub.MainPubSub;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;

/**
 * The {@code RedisManagerImpl} class is responsible for managing the Redis integration
 * within the Velocity proxy.
 * <p>
 * This implementation handles Redis connection pooling, publishing and subscribing to
 * Redis channels, and player data management via Redis. It interacts with the Redis system
 * to perform operations such as saving player data, retrieving player counts, and sending
 * messages across Redis channels.
 * </p>
 */
public class RedisManagerImpl implements RedisManager {

  private static final Logger logger = LoggerFactory.getLogger(RedisManagerImpl.class);

  private JedisPool jedisPool;
  private final Gson gson = new Gson();

  private final boolean enabled;

  private final VelocityServer velocityServer;

  /**
   * Implements the Velocity {@code redis} manager.
   */
  public RedisManagerImpl(VelocityServer server) {

    this.velocityServer = server;
    this.enabled = server.getRedisConfiguration().useRedis();

    if (!enabled) {
      return;
    }

    DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
        .credentials(new DefaultRedisCredentials(server.getRedisConfiguration().username(),
        server.getRedisConfiguration().password())).build();

    HostAndPort address = new HostAndPort(server.getRedisConfiguration().host(),
        server.getRedisConfiguration().port());
    JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
    jedisPoolConfig.setMaxTotal(server.getRedisConfiguration().maximumRedisConnections());
    jedisPoolConfig.setBlockWhenExhausted(false);
    jedisPool = new JedisPool(jedisPoolConfig, address, config);

    new Thread(() -> {
      try (Jedis jedis = jedisPool.getResource()) {
        jedis.subscribe(new MainPubSub(server), "queue_channel");
      }
    }).start();
  }

  @Override
  public void send(Object object) {
    if (!enabled) {
      return;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.publish("velocity_redis_channel", gson.toJson(object));
    } catch (Exception e) {
      logger.error("Something went wrong while trying to send a message through Redis.", e);
    }
  }

  @Override
  public void send(Object object, String channel) {
    if (!enabled) {
      return;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.publish(channel, gson.toJson(object));
    } catch (Exception e) {
      logger.error("Something went wrong while trying to send a message through Redis.", e);
    }
  }

  @Override
  public void savePlayer(Player player, RegisteredServer server) {
    if (!enabled) {
      return;
    }

    String playerKey = "player:" + player.getUniqueId().toString();

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.set(playerKey, player.getUsername());
      System.out.println("setting: " + playerKey + " to: " + player.getUsername());
      jedis.sadd("server:" + server.getServerInfo().getName(), player.getUniqueId().toString());
      System.out.println(jedis.scard("server:" + server.getServerInfo().getName()));
    }
  }

  @Override
  public void removePlayer(Player player) {
    if (!enabled) {
      return;
    }

    ServerConnection connection = player.getCurrentServer().orElse(null);
    if (connection == null) {
      return;
    }

    String playerKey = "player:" + player.getUniqueId().toString();

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.del(playerKey);
      jedis.srem("server:" + connection.getServerInfo().getName(), player.getUniqueId().toString());
    }
  }

  @Override
  public int getPlayerCount(String server) {
    if (!enabled) {
      RegisteredServer registeredServer = velocityServer.getServer(server).orElse(null);
      if (registeredServer != null) {
        return registeredServer.getPlayersConnected().size();
      }
      return 0;
    }

    int amount;

    try (Jedis jedis = jedisPool.getResource()) {
      amount = (int) jedis.scard("server:" + server);
    }
    return amount;
  }

  @Override
  public int getTotalPlayerCount() {
    if (!enabled) {
      return velocityServer.getPlayerCount();
    }

    int count = 0;

    try (Jedis jedis = jedisPool.getResource()) {
      Set<String> serverKeys = jedis.keys("server:*");

      for (String serverKey : serverKeys) {
        count += (int) jedis.scard(serverKey);
      }
    }
    return count;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public List<String> getConnectedPlayerNames(String name) {
    List<String> allUsernames = new ArrayList<>();

    // Get all server keys
    try (Jedis jedis = jedisPool.getResource()) {
      Set<String> serverKeys = jedis.keys("server:*");

      // Iterate through each server key
      for (String serverKey : serverKeys) {
        // Get all player UUIDs connected to this server
        Set<String> playerUuids = jedis.smembers(serverKey);

        // Retrieve and add the username for each UUID
        for (String uuid : playerUuids) {
          String username = jedis.get("player:" + uuid);
          if (username != null) {
            allUsernames.add(username);
          }
        }
      }
    }
    return allUsernames;
  }

  public Gson getGson() {
    return gson;
  }
}
