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
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;

/**
 * Manages the queue system without redis.
 */
public class QueueManagerNoRedisImpl extends QueueManager {

  /**
   * Constructs a {@link QueueManagerRedisImpl}.
   *
   * @param server the proxy server
   */
  public QueueManagerNoRedisImpl(final VelocityServer server) {
    super(server);
  }

  /**
   * Gets the queue of a server, or creates it if it doesn't exist.
   *
   * @param server The server to get the queue of
   * @return The queue of the server.
   */
  public ServerQueueStatus getQueue(final String server) {
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
    return true;
  }

  /**
   * Hook that removes the player from all queues.
   *
   * @param player the disconnecting player
   */
  public void onPlayerLeave(final ConnectedPlayer player) {
    this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
      this.server.getAllServers().forEach(server -> {
        dequeue(player.getUniqueId(), server.getServerInfo().getName(), false);
      });
    })
    .delay(getTimeoutInSeconds(player), TimeUnit.SECONDS)
    .schedule();
  }

  /**
   * Handles removing a player form the queue.
   *
   * @param player The player to remove.
   * @param serverName The server of which to remove the player from the queue.
   * @param command Whether this method was triggered by a player doing a command or not.
   */
  public void dequeue(final UUID player, final String serverName, final boolean command) {
    ServerQueueStatus status = getQueue(serverName);
    if (status == null) {
      throw new IllegalArgumentException("No queue found for server '" + serverName + "'");
    }

    boolean wasQueued = status.isQueued(player);
    status.dequeue(player, false);

    if (command) {
      if (wasQueued) {
        this.server.getPlayer(player).ifPresent(p -> {
          p.sendMessage(Component.translatable("velocity.queue.command.left-queue")
              .arguments(Component.text(serverName)));
        });
      } else {
        this.server.getPlayer(player).ifPresent(p -> {
          p.sendMessage(Component.translatable("velocity.queue.error.not-in-queue")
              .arguments(Component.text(serverName)));
        });
      }
    }
  }

  /**
   * Queues the player into a specific server, without indication.
   *
   * @param player The player to queue
   * @param server The server to queue into
   */
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

    String serverName = server.getServerInfo().getName();
    ServerQueueStatus status = getQueue(serverName);
    if (status == null) {
      throw new IllegalArgumentException("No queue found for server '" + serverName + "'");
    }

    if (status.isPaused() && !this.server.getConfiguration().getQueue().isAllowPausedQueueJoining()) {
      player.sendMessage(Component.translatable("velocity.queue.error.paused")
              .arguments(Component.text(serverName)));
      return;
    }

    if (status.isQueued(player.getUniqueId())) {
      player.sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
          .arguments(
              Component.text(player.getUsername()),
              Component.text(serverName)
          )
      );
      return;
    }

    status.queue(player.getUniqueId(), player.getQueuePriority(server.getServerInfo().getName()),
        player.hasPermission("velocity.queue.full.bypass"));
  }

  /**
   * Updates the actionbar message for this player.
   */
  public void tickMessageForAllPlayers() {
    Map<Player, ServerQueueStatus> temp = new HashMap<>();
    String filter = this.config.getMultipleServerMessagingSelection();

    for (ServerQueueStatus status : this.serverQueues.values()) {
      Map<ServerQueueEntry, UUID> map = status.getActivePlayers();
      for (ServerQueueEntry entry : map.keySet()) {
        UUID player = map.get(entry);

        Player p = server.getPlayer(player).orElse(null);
        if (p == null) {
          return;
        }

        if (filter.equalsIgnoreCase("first") && temp.containsKey(p)) {
          continue;
        }

        temp.put(p, status);

      }
    }

    for (Player player : temp.keySet()) {
      ServerQueueStatus status = temp.get(player);
      ServerQueueEntry entry = status.getEntry(player.getUniqueId()).orElse(null);
      if (entry != null) {
        player.sendActionBar(temp.get(player).getActionBarComponent(entry));
      }
    }
  }

  @Override
  public void removeFromAll(final ConnectedPlayer player) {
    for (ServerQueueStatus status : this.serverQueues.values()) {
      status.dequeue(player.getUniqueId(), false);
    }
  }
}
