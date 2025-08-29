/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

package com.velocitypowered.proxy.queue;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.cache.RedisRetriever;
import com.velocitypowered.proxy.redis.RedisManagerImpl;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueDequeueRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendActionBarRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessageToUuidRequest;
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the queue system with Redis.
 */
public class QueueManagerRedisImpl extends QueueManager {

  /**
   * Logger for Redis queue manager operations.
   */
  private static final Logger logger = LoggerFactory.getLogger(QueueManagerRedisImpl.class);

  /**
   * Constructs a {@link QueueManagerRedisImpl}.
   *
   * @param server the proxy server
   */
  public QueueManagerRedisImpl(final VelocityServer server) {
    super(server);
    this.cache = new RedisRetriever(server);

    this.registerRedisListeners();
  }

  private void registerRedisListeners() {
    RedisManagerImpl redisManager = this.server.getRedisManager();

    redisManager.listen(RedisQueueSendRequest.ID, RedisQueueSendRequest.class, it -> {
      server.getPlayer(it.playerUuid()).ifPresent(player -> {
        RegisteredServer foundServer = server.getServer(it.serverName()).orElse(null);
        if (foundServer == null) {
          throw new IllegalArgumentException("Server not found while attempting to send player in queue. '" + it.serverName() + "'");
        }

        ServerQueueStatus queueStatus = getQueue(foundServer.getServerInfo().getName());
        ServerQueueEntry entry = queueStatus.getEntry(it.playerUuid()).orElse(null);

        if (entry == null) {
          // Cross-proxy: create a minimal temporary entry to perform the send locally.
          logger.debug("Creating temporary queue entry for cross-proxy player {} on server {}", it.playerUuid(), it.serverName());
          RemotePlayerInfo playerInfo = server.getMultiProxyHandler().getPlayerInfo(it.playerUuid());
          int priority = playerInfo != null ? playerInfo.getQueuePriority().getOrDefault(it.serverName(), 0) : 0;
          boolean fullBypass = playerInfo != null && playerInfo.isFullQueueBypass();
          boolean queueBypass = playerInfo != null && playerInfo.isQueueBypass();
          entry = new ServerQueueEntry(it.playerUuid(), (VelocityRegisteredServer) foundServer, server,
                priority, fullBypass, queueBypass);
        }

        entry.handleSending();
      });
    });

    redisManager.listen(RedisQueueDequeueRequest.ID, RedisQueueDequeueRequest.class, it -> {
      logger.debug("Received RedisQueueDequeueRequest for player {} on server {} (maxRetriesReached: {})",
          it.playerUuid(), it.serverName(), it.maxRetriesReached());

      if (!server.getQueueManager().isMasterProxy()) {
        logger.debug("Ignoring dequeue request - not master proxy");
        return;
      }

      RegisteredServer foundServer = server.getServer(it.serverName()).orElse(null);
      if (foundServer == null) {
        logger.error("Server not found while attempting to dequeue player. '{}'", it.serverName());
        throw new IllegalArgumentException("Server not found while attempting to dequeue player. '" + it.serverName() + "'");
      }

      ServerQueueStatus queueStatus = getQueue(foundServer.getServerInfo().getName());
      if (queueStatus != null) {
        // Check if the player is still in the queue before attempting to dequeue
        if (queueStatus.isQueued(it.playerUuid())) {
          logger.debug("Dequeuing player {} from server {} queue", it.playerUuid(), it.serverName());
          queueStatus.dequeue(it.playerUuid(), it.maxRetriesReached());
        } else {
          logger.debug("Player {} not found in queue for server {} (may have been already removed)",
              it.playerUuid(), it.serverName());
        }
      } else {
        logger.warn("Queue status not found for server {}", it.serverName());
      }
    });

    redisManager.listen(RedisSendActionBarRequest.ID, RedisSendActionBarRequest.class, it -> {
      Component component = it.component();

      if (component != null) {
        server.getPlayer(it.playerUuid()).ifPresent(player -> player.sendActionBar(component));
      }
    });

    redisManager.listen(RedisSendMessageToUuidRequest.ID, RedisSendMessageToUuidRequest.class, it -> {
      Component component = it.component();

      if (component != null) {
        server.getPlayer(it.player()).ifPresent(player -> player.sendMessage(component));
      }
    });
  }

  /**
   * Checks whether the current proxy is the current master-proxy or not.
   *
   * @return whether the current proxy is the current master-proxy or not.
   */
  @Override
  public boolean isMasterProxy() {
    List<String> masterProxies = this.server.getConfiguration().getQueue().getMasterProxyIds();
    List<String> activeProxies = new ArrayList<>(this.server.getMultiProxyHandler()
            .getAllProxyIds().stream().toList());
    Collections.sort(activeProxies);

    if (masterProxies == null || masterProxies.isEmpty()
        || (masterProxies.size() == 1 && masterProxies.get(0).trim().isEmpty())) {
      if (activeProxies.size() == 1) {
        String singleProxyId = activeProxies.get(0);
        String ownProxy = this.server.getMultiProxyHandler().getOwnProxyId();
        return singleProxyId.equalsIgnoreCase(ownProxy);
      }

      return false;
    }

    activeProxies.retainAll(masterProxies);

    String ownProxy = this.server.getMultiProxyHandler().getOwnProxyId();

    String firstMasterProxy = null;

    for (String activeProxy : activeProxies) {
      if (masterProxies.contains(activeProxy)) {
        firstMasterProxy = activeProxy;
        break;
      }
    }

    if (firstMasterProxy != null && firstMasterProxy.equalsIgnoreCase(ownProxy)) {
      int ownIndex = masterProxies.indexOf(ownProxy);
      int firstIndex = masterProxies.indexOf(firstMasterProxy);

      return ownIndex != -1 && ownIndex == firstIndex;
    }

    return false;
  }

  /**
   * Updates the actionbar message for all players.
   */
  @Override
  public void tickMessageForAllPlayers() {
    for (ServerQueueStatus status : this.cache.getAll()) {
      status.getActivePlayers().forEach((entry, player) ->
          this.server.getRedisManager().send(new RedisSendActionBarRequest(player, status.getActionBarComponent(entry))));
    }
  }
}
