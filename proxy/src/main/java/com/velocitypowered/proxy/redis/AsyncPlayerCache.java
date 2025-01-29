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
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Remote player info player handling system.
 */
public class AsyncPlayerCache {

  private static final int scanBatchSize = 100;
  private static final ScanParams SCAN_PARAMS = new ScanParams().count(scanBatchSize);

  private final String cacheKey;
  private final ExecutorService redisExecutor;
  private final JedisPool jedisPool;
  private final Gson gson;

  /**
   * Creates a new instance of the cache.
   *
   * @param cacheKey The key in redis.
   * @param jedisPool The redis connection pool.
   * @param gson The gson instance.
   * @param threadPoolSize The size of the thread pool. Preferably around 10.
   */
  public AsyncPlayerCache(final String cacheKey, final JedisPool jedisPool, final Gson gson, final int threadPoolSize) {
    this.cacheKey = cacheKey;
    this.redisExecutor = Executors.newFixedThreadPool(threadPoolSize);
    this.jedisPool = jedisPool;
    this.gson = gson;
  }

  /**
   * Add or update a player async.
   *
   * @param players The players to update.
   *
   * @return A future with no result.
   */
  public CompletableFuture<Void> addOrUpdatePlayers(final List<RemotePlayerInfo> players) {
    if (players.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.runAsync(() -> {
      try (Jedis jedis = jedisPool.getResource()) {
        Pipeline pipeline = jedis.pipelined();
        for (RemotePlayerInfo player : players) {
          String json = gson.toJson(player);
          pipeline.hset(cacheKey, player.getUuid().toString(), json);
        }
        pipeline.sync();
      } catch (Exception e) {
        throw new RuntimeException("Async write failed", e);
      }
    }, redisExecutor);
  }

  /**
   * Remove players from the cache.
   *
   * @param players The players to remove.
   *
   * @return A future with the number of players that were removed.
   */
  public CompletableFuture<Long> removePlayers(final List<RemotePlayerInfo> players) {
    if (players.isEmpty()) {
      return CompletableFuture.completedFuture(0L);
    }

    return CompletableFuture.supplyAsync(() -> {
      String[] uuids = players.stream()
          .map(p -> p.getUuid().toString())
          .toArray(String[]::new);

      try (Jedis jedis = jedisPool.getResource()) {
        return jedis.hdel(cacheKey, uuids);
      } catch (Exception e) {
        throw new RuntimeException("Async delete failed", e);
      }
    }, redisExecutor);
  }

  /**
   * Get all the players in the cache.
   *
   * @return A future with a list of the cache.
   */
  public List<RemotePlayerInfo> getCache() {
    List<RemotePlayerInfo> result = new ArrayList<>();
    String cursor = "0";

    try (Jedis jedis = jedisPool.getResource()) {
      do {
        ScanResult<Map.Entry<String, String>> scanResult =
            jedis.hscan(cacheKey, cursor, SCAN_PARAMS);
        cursor = scanResult.getCursor();
        result.addAll(processEntries(scanResult.getResult()));
      } while (!"0".equals(cursor));
    } catch (Exception e) {
      throw new RuntimeException("Async read failed", e);
    }

    return result;
  }

  private List<RemotePlayerInfo> processEntries(final List<Map.Entry<String, String>> entries) {
    return entries.parallelStream()
        .map(entry -> gson.fromJson(entry.getValue(), RemotePlayerInfo.class))
        .collect(Collectors.toList());
  }

  /**
   * Shuts down the redis connection pool gracefully.
   */
  public void shutdown() {
    redisExecutor.shutdown();
    jedisPool.close();
  }

  /**
   * Add or update a single player from the cache.
   *
   * @param player The player instance.
   *
   * @return A future with no result.
   */
  public CompletableFuture<Void> addOrUpdatePlayer(final RemotePlayerInfo player) {
    return addOrUpdatePlayers(Collections.singletonList(player));
  }

  /**
   * Remove a single player from the cache.
   *
   * @param player The player to remove.
   *
   * @return A future with the amount removed.
   */
  public CompletableFuture<Long> removePlayer(final RemotePlayerInfo player) {
    return removePlayers(Collections.singletonList(player));
  }
}
