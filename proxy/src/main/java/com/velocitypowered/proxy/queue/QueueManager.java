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
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.queue.cache.QueueCacheRetriever;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;

/**
 * The interface (abstract class) that will provide methods for the Queue Manager implementations.
 */
public abstract class QueueManager {
  protected final VelocityServer server;
  protected VelocityConfiguration.Queue config;
  protected ScheduledFuture<?> tickMessageTaskHandle;
  protected ScheduledFuture<?> tickPingingBackendTaskHandle;
  private ScheduledFuture<?> sendingTaskHandle;

  private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() / 8);
  private static final ScheduledExecutorService SERVICE = Executors.newScheduledThreadPool(THREAD_COUNT);

  private final boolean enabled;

  protected QueueCacheRetriever cache = null;

  protected static Map<String, Long> LAST_TURNED_ONLINE_TIME = new HashMap<>();

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

    restartTasks();
  }

  /**
   * Restarts all scheduled tasks for the queue manager.
   */
  public void restartTasks() {
    if (!this.isMasterProxy()) {
      return;
    }

    this.schedulePingingBackend();
    this.scheduleTickMessage();
    this.rescheduleTimerTask();
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

    return cache.get(server);
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
      this.tickMessageTaskHandle.cancel(true);
    }

    this.tickMessageTaskHandle = SERVICE.scheduleAtFixedRate(this::tickMessageForAllPlayers,
        (long) (config.getMessageDelay() * 1000),
        (long) (config.getMessageDelay() * 1000),
        TimeUnit.MILLISECONDS);
  }

  /**
   * Handles starting the task that manages pinging the backend to
   * check if servers are enabled or disabled.
   */
  public void schedulePingingBackend() {
    if (this.tickPingingBackendTaskHandle != null) {
      this.tickPingingBackendTaskHandle.cancel(true);
    }

    this.tickPingingBackendTaskHandle = SERVICE.scheduleAtFixedRate(this::tickPingingBackend,
        (long) (config.getBackendPingInterval() * 1000),
        (long) (config.getBackendPingInterval() * 1000),
        TimeUnit.MILLISECONDS);
  }

  /**
   * Reschedules the task responsible for processing and sending queued players to their destination servers.
   * This method cancels any existing task associated with sending queued players and schedules a new task.
   * The new task runs periodically based on the configured delay specified in the queue settings.
   */
  public void rescheduleTimerTask() {
    if (this.sendingTaskHandle != null) {
      this.sendingTaskHandle.cancel(true);
    }

    this.sendingTaskHandle = SERVICE.scheduleAtFixedRate(this::tickSending,
        (long) (config.getSendDelay() * 1000),
        (long) (config.getSendDelay() * 1000),
        TimeUnit.MILLISECONDS);
  }

  /**
   * Handles the logic for when a player leaves.
   *
   * @param player The player that left.
   */
  public void onPlayerLeave(final ConnectedPlayer player) {
    long timeout = getTimeoutInSeconds(player);

    if (timeout == -1) {
      removeFromAll(player);
    } else {
      this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, ()
          -> removeFromAll(player)).delay(getTimeoutInSeconds(player), TimeUnit.SECONDS).schedule();
    }
  }

  /**
   * Sends the next player in queue, unless the queue is paused.
   */
  public void tickSending() {
    if (!isMasterProxy()) {
      return;
    }

    cache.getAll().forEach(queue -> {
      if (queue.isPaused() || !queue.isOnline()) {
        return;
      }

      if (queue.getQueue().isEmpty()) {
        return;
      }

      ServerQueueEntry entry = queue.getQueue().peekFirst();

      if (entry == null || queue.isFull() && !entry.isFullBypass()) {
        return;
      }

      if (this.server.getMultiProxyHandler().isRedisEnabled()) {
        if (this.server.getMultiProxyHandler().isPlayerOnline(entry.getPlayer())) {
          queue.sendFirstInQueue(entry);
        } else {
          queue.getQueue().pollFirst();
          this.server.getRedisManager().addOrUpdateQueue(queue);
        }
      } else {
        if (this.server.getPlayer(entry.getPlayer()).orElse(null) != null) {
          queue.sendFirstInQueue(entry);
        } else {
          queue.getQueue().pollFirst();
        }
      }
    });
  }

  /**
   * Pings the backend to update the online flag.
   */
  public void tickPingingBackend() {
    List<ServerQueueStatus> queues = this.cache.getAll();
    for (ServerQueueStatus queue : queues) {
      RegisteredServer s = this.server.getServer(queue.getServerName()).orElse(null);
      if (s == null) {
        continue;
      }

      s.ping().whenComplete((result, th) -> {
        double queueDelay = this.server.getConfiguration().getQueue().getQueueDelay() * 1000;

        if (th != null) {
          queue.setStatus(ServerStatus.OFFLINE);
        }

        if (queue.getStatus() == ServerStatus.OFFLINE && th == null) {
          queue.setStatus(ServerStatus.WAITING);
          LAST_TURNED_ONLINE_TIME.put(queue.getServerName(), System.currentTimeMillis());
        }

        if (th == null && System.currentTimeMillis()
            >= LAST_TURNED_ONLINE_TIME.get(queue.getServerName()) + queueDelay
            && queue.getStatus() == ServerStatus.WAITING) {
          queue.setStatus(ServerStatus.ONLINE);
        }

        ServerStatus temp = queue.getStatus();

        if (temp != ServerStatus.ONLINE && queue.isOnline()) {
          for (ServerQueueEntry entry : queue.getQueue()) {
            if (entry.isQueueBypass()) {
              entry.send();
              queue.dequeue(entry.getPlayer(), false);
            }
          }
        }

        if (queue.isOnline()) {
          final int maxPlayers = this.server.getConfiguration().getPlayerCaps().get(queue.getServerName());
          long playerCount;
          if (this.server.getMultiProxyHandler().isRedisEnabled()) {
            playerCount = this.server.getMultiProxyHandler().getAllPlayers().stream().filter(info -> info.getServerName() != null
                && info.getServerName().equalsIgnoreCase(queue.getServerName())).count();
          } else {
            playerCount = server.getPlayerCount();
          }
          queue.setFull(playerCount >= maxPlayers);
        }
      });
    }
  }

  /**
   * Gets the timeout in seconds at which the player will be removed from a queue
   * after leaving the server.
   *
   * @param player The player to get the timeout for.

   * @return The timeout in seconds at which the player will be removed from a queue after leaving the server.
   */
  protected int getTimeoutInSeconds(final ConnectedPlayer player) {
    if (player.hasPermission("velocity.queue.timeout.exempt")) {
      return 0;
    }

    for (int i = 86400; i > 0; i--) {
      if (player.hasPermission("velocity.queue.timeout." + i)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Adds someone to the queue.
   *
   * @param player The player to add to the queue.
   * @param server The server to add the player to the queue to.
  */
  public void queue(final Player player, final VelocityRegisteredServer server) {
    if (!isQueueEnabled() || player.hasPermission("velocity.queue.bypass")) {
      player.createConnectionRequest(server).connectWithIndication();
      return;
    }

    String targetServerName = server.getServerInfo().getName();

    ServerQueueStatus targetQueueStatus = getQueue(targetServerName);
    if (targetQueueStatus != null && targetQueueStatus.isQueued(player.getUniqueId())) {
      player.sendMessage(Component.translatable("velocity.queue.error.already-queued")
          .arguments(Component.text(targetServerName)));
      return;
    }

    if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
      for (ServerQueueStatus status : this.cache.getAll()) {
        if (status.isQueued(player.getUniqueId())) {
          status.dequeue(player.getUniqueId(), false);
          player.sendMessage(Component.translatable("velocity.queue.error.queued-swap")
              .arguments(
                  Component.text(status.getServerName()),
                  Component.text(targetServerName)));
          break;
        }
      }
    }

    ServerQueueStatus status = getQueue(targetServerName);
    if (status == null) {
      throw new IllegalArgumentException("No queue found for server '" + targetServerName + "'");
    }

    if (status.isPaused() && !this.server.getConfiguration().getQueue().isAllowPausedQueueJoining()) {
      player.sendMessage(Component.translatable("velocity.queue.error.paused")
          .arguments(Component.text(targetServerName)));
      return;
    }

    status.queue(player.getUniqueId(), player.getQueuePriority(server.getServerInfo().getName()),
        player.hasPermission("velocity.queue.full.bypass"),
        player.hasPermission("velocity.queue.bypass"));

    player.sendMessage(Component.translatable("velocity.queue.command.queued")
        .arguments(Component.text(targetServerName)));
  }

  /**
   * Sends the actionbar message to all players.
   */
  abstract void tickMessageForAllPlayers();

  /**
   * Reloads the config for every server that has a queue.
   */
  public void reloadConfig() {
    this.config = this.server.getConfiguration().getQueue();
    for (ServerQueueStatus server : this.cache.getAll()) {
      server.reloadConfig();
    }
    restartTasks();
  }

  /**
   * Clears all the queues and stops the tasks.
   */
  public void clearQueue() {
    for (ServerQueueStatus status : this.cache.getAll()) {
      status.stop();
    }

    if (tickMessageTaskHandle != null) {
      tickMessageTaskHandle.cancel(true);
    }

    if (tickPingingBackendTaskHandle != null) {
      tickPingingBackendTaskHandle.cancel(true);
    }

    if (sendingTaskHandle != null) {
      sendingTaskHandle.cancel(true);
    }
  }

  /**
   * Return all the queues.
   *
   * @return All the queues.
   */
  public List<ServerQueueStatus> getAll() {
    return this.cache.getAll();
  }

  /**
   * Returns whether the queue system is enabled or not.
   *
   * @return Whether the queue system is enabled or not.
   */
  public boolean isQueueEnabled() {
    return enabled;
  }

  /**
   * Remove a player from all queues.
   *
   * @param player The player to remove.
   */
  public void removeFromAll(final ConnectedPlayer player) {
    for (ServerQueueStatus status : this.cache.getAll()) {
      if (status.isQueued(player.getUniqueId())) {
        status.dequeue(player.getUniqueId(), false);
      }
    }
  }
}
