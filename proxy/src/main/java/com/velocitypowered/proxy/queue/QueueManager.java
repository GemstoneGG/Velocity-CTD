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
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The interface (abstract class) that will provide methods for the Queue Manager implementations.
 */
public abstract class QueueManager {
  protected final VelocityServer server;
  protected final VelocityConfiguration.Queue config;
  protected ScheduledTask tickMessageTaskHandle;
  protected ScheduledTask tickPingingBackendTaskHandle;
  protected final Map<String, ServerQueueStatus> serverQueues = new HashMap<>();

  private final boolean enabled;

  /**
   * Initializes a new Queue Manager with the proxy and config.
   *
   * @param server The proxy.
  */
  public QueueManager(final VelocityServer server) {
    this.server = server;
    this.config = server.getConfiguration().getQueue();
    this.enabled = config.isEnabled();

    if (!config.isEnabled()) {
      return;
    }

    this.schedulePingingBackend();
    this.scheduleTickMessage();
  }

  /**
   * Returns the queue based on the server name, or creates on if it doesn't exist.
   *
   * @param server The name of the server to get the queue from.
   * @return The queue for the server.
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
   * Returns whether this is the master proxy in the Redis cluster or not.
   * If redis is disabled, this will always return true.
   *
   * @return Whether this proxy is the master proxy or not, or true if redis is disabled.
   */
  public abstract boolean isMasterProxy();

  /**
   * Handles starting the task that manages sending the actionbar
   * messages to the players in the queues.
   */
  public void scheduleTickMessage() {
    if (this.tickMessageTaskHandle != null) {
      this.tickMessageTaskHandle.cancel();
    }

    this.tickMessageTaskHandle = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::tickMessageForAllPlayers)
        .repeat((int) (config.getMessageDelay() * 1000), TimeUnit.MILLISECONDS)
        .schedule();
  }

  /**
   * Handles starting the task that manages pinging the backend to
   * check if servers are enabled or disabled.
   */
  public void schedulePingingBackend() {
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
        .repeat((int) (config.getBackendPingInterval() * 1000), TimeUnit.MILLISECONDS)
        .schedule();
  }

  /**
   * Handles the logic for when a player leaves.
   *
   * @param player The player that left.
   */
  public abstract void onPlayerLeave(ConnectedPlayer player);

  /**
   * Gets the timeout in seconds at which the player will be removed from a queue
   * after leaving the server.
   *
   * @param player The player to get the timeout for.

   * @return The timeout in seconds at which the player will be removed from a queue after leaving the server.
   */
  protected int getTimeoutInSeconds(final ConnectedPlayer player) {
    for (int i = 86400; i > 0; i--) {
      if (player.hasPermission("velocity.queue.timeout." + i)) {
        return i;
      }
    }
    return 1;
  }

  /**
   * Adds someone to the queue.
   *
   * @param player The player to add to the queue.
   * @param server The server to add the player to the queue to.
  */
  public abstract void queue(Player player, VelocityRegisteredServer server);

  /**
   * Sends the actionbar message to all players.
   */
  abstract void tickMessageForAllPlayers();

  /**
   * Reloads the config for every server that has a queue.
   */
  public void reloadConfig() {
    for (ServerQueueStatus server : this.serverQueues.values()) {
      server.reloadConfig();
    }
  }

  /**
   * Clears all the queues and stops the tasks.
   */
  public void clearQueue() {
    for (ServerQueueStatus status : this.serverQueues.values()) {

      status.stop();
    }

    if (tickMessageTaskHandle != null) {
      tickMessageTaskHandle.cancel();
    }

    if (tickPingingBackendTaskHandle != null) {
      tickPingingBackendTaskHandle.cancel();
    }
  }

  /**
   * Return all the queues.
   *
   * @return All the queues.
   */
  public List<ServerQueueStatus> getAll() {
    return this.serverQueues.values().stream().toList();
  }

  /**
   * Returns whether the queue system is enabled or not.
   *
   * @return Whether the queue system is enabled or not.
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Remove a player from all queues.
   *
   * @param player The player to remove.
   */
  public abstract void removeFromAll(ConnectedPlayer player);
}
