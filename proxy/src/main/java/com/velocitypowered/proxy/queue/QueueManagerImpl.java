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
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.redis.RedisManagerImpl;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueAddRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueAlreadyJoinedRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueDisableWaitingForConnectionRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueLeaveRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendStatusRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendActionBarRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessageToUuidRequest;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;

/**
 * Manages the queue system.
 */
public class QueueManagerImpl {
  private final VelocityServer server;
  private final VelocityConfiguration.Queue config;
  private ScheduledTask tickMessageTaskHandle;
  private ScheduledTask tickPingingBackendTaskHandle;

  // Map of servers connected to its queue status.
  private final Map<String, ServerQueueStatus> serverQueues = new HashMap<>();

  /**
   * Constructs a {@link QueueManagerImpl}.
   *
   * @param server the proxy server
   */
  public QueueManagerImpl(final VelocityServer server) {
    this.server = server;
    config = server.getConfiguration().getQueue();

    if (!config.isEnabled()) {
      return;
    }

    this.registerRedisListeners();
    this.schedulePingingBackend();
    this.scheduleTickMessage();
  }

  private void registerRedisListeners() {
    RedisManagerImpl redisManager = this.server.getRedisManager();

    redisManager.listen(RedisQueueAddRequest.ID, RedisQueueAddRequest.class, it -> {
      if (!isMasterProxy()) {
        System.out.println("Not master proxy");
        return;
      }

      ServerQueueStatus status = getQueue(it.serverName());
      if (status == null) {
        throw new IllegalArgumentException("No queue found for server '" + it.serverName() + "'");
      }

      if (status.isQueued(it.playerUuid())) {
        redisManager.send(new RedisQueueAlreadyJoinedRequest(it.playerUuid(), it.serverName()));
        return;
      }

      status.queue(it.playerUuid());
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
      status.dequeue(it.playerUuid());

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
          System.out.println("Sending status update for "
                  + it.playerUuid() + " cause server does not exist");
          redisManager.send(new RedisQueueSendStatusRequest(it.playerUuid(), it.serverName(), false,
                  it.playerUuid()));
        } else {
          System.out.println("Creating connection request");
          player.createConnectionRequest(foundServer).connectWithIndication().thenAccept(result -> {
            System.out.println("Sending status update for "
                    + it.playerUuid() + " with result: " + result);
            redisManager.send(new RedisQueueSendStatusRequest(it.playerUuid(), it.serverName(),
                    result, it.playerUuid()));
          }).exceptionally(ex -> {
            System.out.println("Sending status update in exception");
            redisManager.send(new RedisQueueSendStatusRequest(it.playerUuid(), it.serverName(),
                    false, it.playerUuid()));
            return null;
          });
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
        System.out.println("sending disable waiting for connection request");
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
        server.getPlayer(it.playerUuid()).ifPresent(player -> {
          player.sendActionBar(component);
        });
      }
    });

    redisManager.listen(RedisSendMessageToUuidRequest.ID,
            RedisSendMessageToUuidRequest.class, it -> {
        Component component = it.component();

        if (component != null) {
          server.getPlayer(it.player()).ifPresent(player -> {
            player.sendMessage(component);
          });
        }
      });

    redisManager.listen(RedisQueueDisableWaitingForConnectionRequest.ID, RedisQueueDisableWaitingForConnectionRequest.class, it -> {
      if (!isMasterProxy()) {
        return;
      }

      ServerQueueStatus queue = getQueue(it.serverName());

      System.out.println("queue: " + queue);
      if (queue == null) {
        return;
      }

      System.out.println("getting entry");

      queue.getEntry(it.playerUuid()).ifPresent(entry -> {
        System.out.println("setting waiting for connection to false");
        entry.waitingForConnection = false;
        entry.connectionAttempts += 1;

        if (entry.connectionAttempts == this.server.getConfiguration().getQueue().getMaxSendRetries()) {
          this.server.getRedisManager().send(new RedisQueueLeaveRequest(it.playerUuid(), it.serverName(), false));
        }
      });
    });
  }

  /**
   * Gets the queue of a server, or creates it if it doesn't exist.
   *
   * @param server The server to get the queue of
   *
   * @return The queue of the server.
   */
  public ServerQueueStatus getQueue(String server) {
    RegisteredServer registeredServer = this.server.getServer(server).orElse(null);
    if (registeredServer == null) {
      return null;
    }
    return serverQueues.computeIfAbsent(server, status ->
            new ServerQueueStatus((VelocityRegisteredServer) registeredServer, this.server));
  }

  /**
   * Checks whether the current proxy is the current master-proxy or not.
   *
   * @return whether the current proxy is the current master-proxy or not.
   */
  public boolean isMasterProxy() {
    List<String> masterProxies = this.server.getConfiguration().getQueue().getMasterProxyIds();
    List<String> activeProxies = new ArrayList<>(this.server.getMultiProxyHandler()
            .getAllProxyIds().stream().toList());
    String ownProxy = this.server.getMultiProxyHandler().getOwnProxyId();

    int index = -1;

    for (int i = 0; i < masterProxies.size(); i++) {
      if (masterProxies.get(i).equalsIgnoreCase(ownProxy)) {
        index = i;
      }
    }

    for (String activeProxy : activeProxies) {
      for (int j = 0; j < masterProxies.size(); j++) {
        if (activeProxy.equalsIgnoreCase(ownProxy)) {
          continue;
        }

        if (activeProxy.equalsIgnoreCase(masterProxies.get(j))) {
          if (index > j) {
            return false;
          }
        }
      }
    }

    return true;
  }

  private void scheduleTickMessage() {
    if (this.tickMessageTaskHandle != null) {
      this.tickMessageTaskHandle.cancel();
    }

    this.tickMessageTaskHandle = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::tickMessageForAllPlayers)
        .repeat((long) config.getMessageDelay() * 1000, TimeUnit.MILLISECONDS)
        .schedule();
  }

  private void schedulePingingBackend() {
    if (this.tickPingingBackendTaskHandle != null) {
      this.tickPingingBackendTaskHandle.cancel();
    }

    this.tickPingingBackendTaskHandle = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
          for (RegisteredServer serverApi : this.server.getAllServers()) {
            VelocityRegisteredServer server = (VelocityRegisteredServer) serverApi;
            ServerQueueStatus queueStatus = getQueue(server.getServerInfo().getName());
            queueStatus.tickPingingBackend();
          }
        })
        .repeat((long) config.getBackendPingInterval() * 1000, TimeUnit.MILLISECONDS)
        .schedule();
  }

  /**
   * Hook that is invoked to reload the server configuration.
   */
  public void reloadConfig() {
    for (ServerQueueStatus server : this.serverQueues.values()) {
      server.reloadConfig();
    }
  }

  /**
   * Hook that removes the player from all queues.
   *
   * @param player the disconnecting player
   */
  public void onPlayerLeave(final ConnectedPlayer player) {
    this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
      this.server.getAllServers().forEach(server -> {
        this.server.getRedisManager().send(new RedisQueueLeaveRequest(player.getUniqueId(),
                server.getServerInfo().getName(),
                false));
      });
    })
            .delay(getTimeoutInSeconds(player), TimeUnit.SECONDS)
            .schedule();
  }

  private int getTimeoutInSeconds(final ConnectedPlayer player) {
    for (int i = 86400; i > 0; i--) {
      if (player.hasPermission("velocity.queue.timeout." + i)) {
        return i;
      }
    }
    return 1;
  }

  /**
   * Queues the player into a specific server, without indication.
   *
   * @param player The player to queue
   * @param server The server to queue into
   */
  public void queue(Player player, VelocityRegisteredServer server) {
    System.out.println("sending add request to redis for: " + player.getUsername());
    this.server.getRedisManager().send(new RedisQueueAddRequest(player.getUniqueId(),
            server.getServerInfo().getName()));
  }

  /**
   * Updates the actionbar message for this player.
   */
  public void tickMessageForAllPlayers() {
    for (ServerQueueStatus status : this.serverQueues.values()) {
      status.getActivePlayers().forEach((entry, player) -> {
        this.server.getRedisManager().send(new RedisSendActionBarRequest(player,
                status.getActionBarComponent(entry)));
      });
    }
  }
}
