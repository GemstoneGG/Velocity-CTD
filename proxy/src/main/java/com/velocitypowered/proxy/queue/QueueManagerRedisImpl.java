/*
 * Copyright (C) 2020-2024 Velocity Contributors
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

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.redis.RedisManagerImpl;
import com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler;
import com.velocitypowered.proxy.redis.multiproxy.RedisPlayerSetQueuedServerRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueAddRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueAlreadyJoinedRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueDisableWaitingForConnectionRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueLeaveRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueuePauseRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendStatusRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendActionBarRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessageToUuidRequest;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;

/**
 * Manages the queue system with redis.
 */
public class QueueManagerRedisImpl extends QueueManager {

  /**
   * Constructs a {@link QueueManagerRedisImpl}.
   *
   * @param server the proxy server
   */
  public QueueManagerRedisImpl(final VelocityServer server) {
    super(server);
    this.registerRedisListeners();
  }

  private void registerRedisListeners() {
    RedisManagerImpl redisManager = this.server.getRedisManager();

    redisManager.listen(RedisQueueAddRequest.ID, RedisQueueAddRequest.class, it -> {
      if (!isMasterProxy()) {
        return;
      }

      ServerQueueStatus status = getQueue(it.serverName());
      if (status == null) {
        throw new IllegalArgumentException("No queue found for server '" + it.serverName() + "'");
      }

      if (status.isPaused() && !this.server.getConfiguration().getQueue().isAllowPausedQueueJoining()) {
        this.server.getRedisManager().send(new RedisSendMessageToUuidRequest(it.playerUuid(),
            Component.translatable("velocity.queue.error.paused")
                .arguments(Component.text(it.serverName()))));
        return;
      }

      if (it.alreadyQueuedMessage()) {
        if (status.isQueued(it.playerUuid())) {
          redisManager.send(new RedisQueueAlreadyJoinedRequest(it.playerUuid(), it.serverName()));
          return;
        }
      }

      redisManager.send(new RedisPlayerSetQueuedServerRequest(it.playerUuid(), it.serverName()));
      status.queue(it.playerUuid(), it.priority(), it.fullBypass());
    });

    redisManager.listen(RedisPlayerSetQueuedServerRequest.ID, RedisPlayerSetQueuedServerRequest.class, it -> {
      MultiProxyHandler.RemotePlayerInfo info = this.server.getMultiProxyHandler().getPlayerInfo(it.player());
      if (info != null) {
        info.setQueuedServer(it.server());
      }
    });

    redisManager.listen(RedisQueueLeaveRequest.ID, RedisQueueLeaveRequest.class, it -> {
      if (!isMasterProxy()) {
        return;
      }

      ServerQueueStatus status = getQueue(it.serverName());
      if (status == null) {
        throw new IllegalArgumentException("No queue found for server '" + it.serverName() + "'");
      }

      boolean wasQueued = status.isQueued(it.playerUuid());
      status.dequeue(it.playerUuid(), false);
      redisManager.send(new RedisPlayerSetQueuedServerRequest(it.playerUuid(), null));

      if (it.command()) {
        if (wasQueued) {
          redisManager.send(new RedisSendMessageToUuidRequest(it.playerUuid(),
                  Component.translatable("velocity.queue.command.left-queue")
                  .arguments(Component.text(it.serverName()))));
        } else {
          redisManager.send(new RedisSendMessageToUuidRequest(it.playerUuid(),
                  Component.translatable("velocity.queue.error.not-in-queue")
                  .arguments(Component.text(it.serverName()))));
        }
      }
    });

    redisManager.listen(RedisQueueSendRequest.ID, RedisQueueSendRequest.class, it -> {
      server.getPlayer(it.playerUuid()).ifPresent(player -> {
        RegisteredServer foundServer = server.getServer(it.serverName()).orElse(null);

        if (foundServer == null) {
          redisManager.send(new RedisQueueSendStatusRequest(it.playerUuid(), it.serverName(), false,
                  it.playerUuid()));
        } else {
          if (this.server.getConfiguration().getQueue().isForwardKickReason()) {
            player.createConnectionRequest(foundServer).connectWithIndication().thenAccept(result -> {
              redisManager.send(new RedisQueueSendStatusRequest(it.playerUuid(), it.serverName(),
                  result, it.playerUuid()));
            }).exceptionally(ex -> {
              redisManager.send(new RedisQueueSendStatusRequest(it.playerUuid(), it.serverName(),
                  false, it.playerUuid()));
              return null;
            });
          } else {
            player.createConnectionRequest(foundServer).connect().thenAccept(result -> {
              redisManager.send(new RedisQueueSendStatusRequest(it.playerUuid(), it.serverName(),
                  result.isSuccessful(), it.playerUuid()));
            }).exceptionally(ex -> {
              redisManager.send(new RedisQueueSendStatusRequest(it.playerUuid(), it.serverName(),
                  false, it.playerUuid()));
              return null;
            });
          }
        }
      });
    });

    redisManager.listen(RedisQueueSendStatusRequest.ID, RedisQueueSendStatusRequest.class, it -> {
      if (!isMasterProxy()) {
        return;
      }

      ServerQueueStatus status = getQueue(it.serverName());
      if (status == null) {
        throw new IllegalArgumentException("No queue found for server '" + it.serverName() + "'");
      }

      if (!it.successfulTransfer()) {
        redisManager.send(new RedisQueueDisableWaitingForConnectionRequest(it.playerUuid(),
                it.serverName()));
      } else {
        redisManager.send(new RedisQueueLeaveRequest(it.playerUuid(), it.serverName(), false));
      }
    });

    redisManager.listen(RedisQueueAlreadyJoinedRequest.ID, RedisQueueAlreadyJoinedRequest.class, it -> {
      ServerQueueStatus status = getQueue(it.serverName());
      if (status == null) {
        throw new IllegalArgumentException("No queue found for server '" + it.serverName() + "'");
      }
      server.getPlayer(it.uuid()).ifPresent(player -> {
        player.sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
                .arguments(
                        Component.text(player.getUsername()),
                        Component.text(it.serverName())
                )
        );
      });
    });

    redisManager.listen(RedisSendActionBarRequest.ID, RedisSendActionBarRequest.class, it -> {
      Component component = it.component();

      if (component != null) {
        server.getPlayer(it.playerUuid()).ifPresent(player -> player.sendActionBar(component));
      }
    });

    redisManager.listen(RedisSendMessageToUuidRequest.ID,
            RedisSendMessageToUuidRequest.class, it -> {
        Component component = it.component();

        if (component != null) {
          server.getPlayer(it.player()).ifPresent(player -> player.sendMessage(component));
        }
      });

    redisManager.listen(RedisQueueDisableWaitingForConnectionRequest.ID, RedisQueueDisableWaitingForConnectionRequest.class, it -> {
      if (!isMasterProxy()) {
        return;
      }

      ServerQueueStatus queue = getQueue(it.serverName());

      if (queue == null) {
        return;
      }


      queue.getEntry(it.playerUuid()).ifPresent(entry -> {
        entry.waitingForConnection = false;
        entry.connectionAttempts += 1;

        if (entry.connectionAttempts == this.server.getConfiguration().getQueue().getMaxSendRetries()) {
          this.server.getRedisManager().send(new RedisQueueLeaveRequest(it.playerUuid(), it.serverName(), false));
          this.server.getRedisManager().send(new RedisSendMessageToUuidRequest(it.playerUuid(),
              Component.translatable("velocity.queue.error.max-send-retries-reached")
                  .arguments(Component.text(it.serverName()),
                      Component.text(this.server.getConfiguration().getQueue()
                          .getMaxSendRetries()))));
        }
      });
    });

    redisManager.listen(RedisQueuePauseRequest.ID, RedisQueuePauseRequest.class, it -> {
      ServerQueueStatus status = getQueue(it.server());
      if (status != null) {
        status.setPaused(it.pause());
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
   * Hook that removes the player from all queues.
   *
   * @param player the disconnecting player
   */
  @Override
  public void onPlayerLeave(final ConnectedPlayer player) {
    this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> this.server.getAllServers().forEach(server
        -> this.server.getRedisManager().send(new RedisQueueLeaveRequest(player.getUniqueId(),
            server.getServerInfo().getName(),
            false))))
            .delay(getTimeoutInSeconds(player), TimeUnit.SECONDS)
            .schedule();
  }

  /**
   * Queues the player into a specific server, without indication.
   *
   * @param player The player to queue
   * @param server The server to queue into
   */
  @Override
  public void queue(final Player player, final VelocityRegisteredServer server) {
    if (!isEnabled() || player.hasPermission("velocity.queue.bypass")) {
      player.createConnectionRequest(server).connectWithIndication();
      return;
    }

    if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
      for (ServerQueueStatus status : this.serverQueues.values()) {
        if (status.isQueued(player.getUniqueId())) {
          player.sendMessage(Component.translatable("velocity.queue.error.multi-queue"));
          return;
        }
      }
    }

    this.server.getRedisManager().send(new RedisQueueAddRequest(player.getUniqueId(),
            server.getServerInfo().getName(), player.getQueuePriority(server.getServerInfo().getName()), true,
        player.hasPermission("velocity.queue.full.bypass")));
  }

  /**
   * Updates the actionbar message for this player.
   */
  @Override
  public void tickMessageForAllPlayers() {
    for (ServerQueueStatus status : this.serverQueues.values()) {
      status.getActivePlayers().forEach((entry, player) -> {
        this.server.getRedisManager().send(new RedisSendActionBarRequest(player,
                status.getActionBarComponent(entry)));
      });
    }
  }

  @Override
  public void removeFromAll(final ConnectedPlayer player) {
    for (ServerQueueStatus status : this.getAll()) {
      if (status.isQueued(player.getUniqueId())) {
        this.server.getRedisManager().send(new RedisQueueLeaveRequest(player.getUniqueId(), status.getServerName(),
            false));
      }
    }
  }
}
