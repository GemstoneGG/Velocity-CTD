package com.velocitypowered.proxy.redis;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.redis.RedisManager;
import com.velocitypowered.proxy.VelocityServer;
import redis.clients.jedis.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RedisManagerImpl implements RedisManager {
    private JedisPool jedisPool;
    private final Gson gson = new Gson();

    private final boolean enabled;

    private final VelocityServer velocityServer;

    public RedisManagerImpl(VelocityServer server){
        this.velocityServer = server;
        this.enabled = server.getRedisConfiguration().isUseRedis();
        if(!enabled) return;

        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .credentials(new DefaultRedisCredentials(server.getRedisConfiguration().getUsername(),
                        server.getRedisConfiguration().getPassword())).build();

        HostAndPort address = new HostAndPort(server.getRedisConfiguration().getHost(),
                server.getRedisConfiguration().getPort());
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(server.getRedisConfiguration().getMaximumRedisConnections());
        jedisPoolConfig.setBlockWhenExhausted(false);
        jedisPool = new JedisPool(jedisPoolConfig, address, config);
    }

    @Override
    public void send(Object object){
        if(!enabled) return;

        try(Jedis jedis = jedisPool.getResource()) {
            jedis.publish("velocity_redis_channel", gson.toJson(object));
        }catch(Exception e){
            System.out.println("Something went wrong trying to sent a message through redis.");
            e.printStackTrace();
        }
    }

    @Override
    public void send(Object object, String channel){
        if(!enabled) return;

        try(Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, gson.toJson(object));
        }catch(Exception e){
            System.out.println("Something went wrong trying to sent a message through redis.");
            e.printStackTrace();
        }
    }

    @Override
    public void savePlayer(Player player) {
        if(!enabled) return;

        ServerConnection connection = player.getCurrentServer().orElse(null);
        if(connection == null) return;
        String playerKey = "player:" + player.getUniqueId().toString();

        try(Jedis jedis = jedisPool.getResource()){
            jedis.set(playerKey, player.getUsername());
            jedis.sadd("server:" + connection.getServerInfo().getName(), player.getUniqueId().toString());
        }
    }

    @Override
    public void removePlayer(Player player) {
        if(!enabled) return;

        ServerConnection connection = player.getCurrentServer().orElse(null);
        if(connection == null) return;

        String playerKey = "player:" + player.getUniqueId().toString();

        try(Jedis jedis = jedisPool.getResource()){
            jedis.del(playerKey);
            jedis.srem("server:" + connection.getServerInfo().getName(), player.getUniqueId().toString());
        }
    }

    @Override
    public int getPlayerCount(String server){
        if(!enabled){
            RegisteredServer registeredServer = velocityServer.getServer(server).orElse(null);
            if(registeredServer != null){
                return registeredServer.getPlayersConnected().size();
            }
            return 0;
        }

        int amount;
        try(Jedis jedis = jedisPool.getResource()){
            amount = (int)jedis.scard("server:" + server);
        }
        return amount;
    }

    @Override
    public int getTotalPlayerCount() {
        if(!enabled){
            return velocityServer.getPlayerCount();
        }

        int count = 0;
        try(Jedis jedis = jedisPool.getResource()){
            Set<String> serverKeys = jedis.keys("server:*");

            for(String serverKey : serverKeys){
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
        try(Jedis jedis = jedisPool.getResource()) {
            Set<String> serverKeys = jedis.keys("server:*");

            // Iterate through each server key
            for (String serverKey : serverKeys) {
                // Get all player UUIDs connected to this server
                Set<String> playerUUIDs = jedis.smembers(serverKey);

                // Retrieve and add the username for each UUID
                for (String uuid : playerUUIDs) {
                    String username = jedis.get("player:" + uuid);
                    if (username != null) {
                        allUsernames.add(username);
                    }
                }
            }

        }
        return allUsernames;
    }

}
