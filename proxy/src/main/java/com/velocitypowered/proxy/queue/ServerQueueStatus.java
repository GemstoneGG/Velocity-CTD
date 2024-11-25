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
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.redis.multiproxy.RedisPlayerSetQueuedServerRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessageToUuidRequest;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Holds queue state for a single backend server.
 */
public class ServerQueueStatus {
  private final VelocityRegisteredServer server;
  private final VelocityServer velocityServer;
  private VelocityConfiguration.@MonotonicNonNull Queue config;
  private final Deque<ServerQueueEntry> queue = new ConcurrentLinkedDeque<>();
  private boolean online = true;
  private boolean paused = false;
  private boolean full = false;
  private ScheduledTask sendingTaskHandle = null;

  /**
   * Constructs a {@link ServerQueueStatus} instance.
   *
   * @param server the backend server
   * @param velocityServer the proxy server
   */
  public ServerQueueStatus(final VelocityRegisteredServer server,
                           final VelocityServer velocityServer) {
    this.server = server;
    this.velocityServer = velocityServer;
    this.reloadConfig();
  }

  /**
   * Stops the queue.
   */
  public void stop() {
    for (ServerQueueEntry entry : this.queue) {
      this.velocityServer.getRedisManager().send(new RedisPlayerSetQueuedServerRequest(entry.player, null));
    }
    if (sendingTaskHandle != null) {
      sendingTaskHandle.cancel();
    }
  }

  private void rescheduleTimerTask() {
    if (this.sendingTaskHandle != null) {
      this.sendingTaskHandle.cancel();
    }

    this.sendingTaskHandle = this.velocityServer.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::tickSending)
        .repeat((int) (this.config.getSendDelay() * 1000), TimeUnit.MILLISECONDS)
        .schedule();
  }

  /**
   * Called by {@link QueueManagerRedisImpl} when the proxy config is reloaded.
   */
  void reloadConfig() {
    this.config = this.velocityServer.getConfiguration().getQueue();
    this.rescheduleTimerTask();
  }

  private void sendFirstInQueue() {

    ServerQueueEntry entry = queue.peekFirst();

    if (entry == null) {
      return;
    }

    if (entry.waitingForConnection) {
      return;
    }

    if (velocityServer.getMultiProxyHandler().isEnabled()) {
      if (!velocityServer.getMultiProxyHandler().isPlayerOnline(entry.player)) {
        this.velocityServer.getRedisManager().send(new RedisPlayerSetQueuedServerRequest(entry.player, null));
        queue.removeFirst();
        return;
      }
    } else {
      queue.removeFirst();
    }

    entry.send();
  }

  /**
   * Sends the next player in queue, unless the queue is paused.
   */
  private void tickSending() {
    if (paused || !online) {
      return;
    }

    if (queue.isEmpty()) {
      return;
    }

    ServerQueueEntry entry = queue.peekFirst();


    if (entry == null || full && !entry.fullBypass) {
      return;
    }

    if (velocityServer.getMultiProxyHandler().isEnabled()) {
      if (velocityServer.getMultiProxyHandler().isPlayerOnline(entry.player)) {
        sendFirstInQueue();
      }
    } else {
      if (velocityServer.getPlayer(entry.player).orElse(null) != null) {
        sendFirstInQueue();
      }
    }
  }

  /**
   * Pings the backend to update the online flag.
   */
  public void tickPingingBackend() {
    server.ping().whenComplete((result, th) -> {
      if (!online && th == null) {
        for (ServerQueueEntry entry : queue) {
          if (entry.priority == -1) {
            entry.send();
          }
        }
      }
      online = th == null;

      if (online) {
        final int maxPlayers = this.velocityServer.getConfiguration().getPlayerCaps().get(server.getServerInfo().getName());
        long playerCount;
        if (this.velocityServer.getMultiProxyHandler().isEnabled()) {
          playerCount = this.velocityServer.getMultiProxyHandler().getAllPlayers().stream().filter(info -> info.getServerName() != null
              && info.getServerName().equalsIgnoreCase(server.getServerInfo().getName())).count();
        } else {
          playerCount = server.getPlayerCount();
        }
        full = playerCount >= maxPlayers;
      }
    });
  }

  /**
   * Generate the ETA component.
   *
   * @param position pos in queue.
   * @return ETA component.
   */
  public Component calculateEta(final int position) {
    int delayInSeconds = (int) this.config.getSendDelay() * position;

    return QueueTimeFormatter.format(Math.max(delayInSeconds, 0));
  }

  /**
   * Returns whether this queue is paused.
   *
   * @return whether this queue is paused
   */
  public boolean isPaused() {
    return paused;
  }

  /**
   * Sets whether this queue is paused.
   *
   * @param paused whether this queue is paused
   */
  public void setPaused(final boolean paused) {
    this.paused = paused;
  }


  /**
   * Queues a player onto this server.
   *
   * @param playerUuid the UUID of the player to queue
   * @param priority The priority with which the player should be added.
   */
  public void queue(final UUID playerUuid, final int priority, boolean fullBypass) {
    if (!config.isEnabled()) {
      Player player = server.getPlayer(playerUuid);
      if (player != null) {
        player.createConnectionRequest(server).connect();
      } else {
        if (this.velocityServer.getMultiProxyHandler().isEnabled()) {
          this.velocityServer.getRedisManager().send(new RedisQueueSendRequest(playerUuid,
              server.getServerInfo().getName()));
        }
      }
      return;
    }

    ServerQueueEntry entry = new ServerQueueEntry(playerUuid, this.server, this.velocityServer, priority, fullBypass);

    synchronized (queue) {
      var iterator = queue.iterator();
      boolean inserted = false;
      int position = 0;

      while (iterator.hasNext()) {
        ServerQueueEntry currentEntry = iterator.next();

        if (currentEntry.priority < priority) {
          insertAtPosition(entry, position);
          inserted = true;
          break;
        }
        position++;
      }

      if (!inserted) {
        queue.addLast(entry);
      }
    }

    if (this.sendingTaskHandle == null) {
      //sendFirstInQueue();
      this.rescheduleTimerTask();
    }
  }

  private void insertAtPosition(final ServerQueueEntry newEntry, final int position) {
    var tempQueue = new ConcurrentLinkedDeque<ServerQueueEntry>();
    int index = 0;

    for (ServerQueueEntry entry : queue) {
      if (index == position) {
        tempQueue.add(newEntry);
      }
      tempQueue.add(entry);
      index++;
    }

    queue.clear();
    queue.addAll(tempQueue);
  }

  /**
   * Removes a player from this queue.
   *
   * @param player the player to remove
   */
  public void dequeue(final UUID player, boolean maxRetriesReached) {
    this.velocityServer.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
      if (maxRetriesReached) {
        if (this.velocityServer.getMultiProxyHandler().isEnabled()) {
          this.velocityServer.getRedisManager().send(new RedisSendMessageToUuidRequest(player,
              Component.translatable("velocity.queue.error.max-send-retries-reached")
                  .arguments(Component.text(getServerName()),
                      Component.text(this.velocityServer.getConfiguration().getQueue().getMaxSendRetries()))));
        } else {
          this.velocityServer.getPlayer(player).ifPresent(p -> {
            p.sendMessage(Component.translatable("velocity.queue.error.max-send-retries-reached")
                .arguments(Component.text(getServerName()),
                    Component.text(this.velocityServer.getConfiguration().getQueue().getMaxSendRetries())));
          });
        }
      }
    }).delay(1, TimeUnit.SECONDS).schedule();

    this.queue.removeIf(entry -> entry.player.equals(player));
  }

  /**
   * Gets the {@link ServerQueueEntry} for the player.
   *
   * @param playerUuid The UUID of the player.
   *
   * @return The {@link ServerQueueEntry} for the player.
   */
  public Optional<ServerQueueEntry> getEntry(final UUID playerUuid) {
    for (ServerQueueEntry entry : queue) {
      if (entry.player.equals(playerUuid)) {
        return Optional.of(entry);
      }
    }
    return Optional.empty();
  }

  /**
   * Creates a descriptive component to place in the result of {@code /queueadmin listqueues}.
   *
   * @return a descriptive component
   */
  public Component createListComponent() {
    return Component.translatable("velocity.queue.command.listqueues.item")
        .arguments(Component.text(server.getServerInfo().getName())
            .hoverEvent(Component.translatable("velocity.queue.command.listqueues.hover")
                .arguments(
                    Component.text(queue.size()),
                    Component.text(paused ? "True" : "False"),
                    Component.text(online ? "True" : "False")
                ).asHoverEvent()
            )
        );
  }

  /**
   * Sends a message in chat to all queued players.
   *
   * @param component the component to send as a message
   */
  public void broadcast(final Component component) {
    for (ServerQueueEntry status : queue) {
      this.velocityServer.getPlayer(status.player).ifPresent(player ->
                player.sendMessage(component));
    }
  }

  /**
   * Returns whether a player is queued.
   *
   * @param playerUuid the player uuid to check
   * @return whether they are queued
   */
  public boolean isQueued(final UUID playerUuid) {
    for (ServerQueueEntry queueStatus : queue) {
      if (queueStatus.player.equals(playerUuid)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns whether this queue is active (not in the {@code no-queue-servers} list).
   *
   * @return whether this queue is active
   */
  public boolean hasQueue() {
    return !config.getNoQueueServers().contains(this.server.getServerInfo().getName());
  }

  /**
   * Returns the actionbar component for this server queue for the given entry.
   *
   * @param entry the entry to generate a component for
   * @return the component to display to the player
   */
  public Component getActionBarComponent(final ServerQueueEntry entry) {
    int position = getQueuePosition(entry.player);
    if (full && !entry.fullBypass) {
      return Component.translatable("velocity.queue.player-status.full", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(entry.target.getServerInfo().getName()),
              calculateEta(position)
          );
    } else if (entry.waitingForConnection) {
      return Component.translatable("velocity.queue.player-status.connecting",
                      NamedTextColor.YELLOW)
          .arguments(Component.text(entry.target.getServerInfo().getName()));
    } else if (paused) {
      return Component.translatable("velocity.queue.player-status.paused", NamedTextColor.YELLOW);
    } else if (online) {
      return Component.translatable("velocity.queue.player-status.online", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(entry.target.getServerInfo().getName()),
              calculateEta(position)
          );
    } else {
      return Component.translatable("velocity.queue.player-status.offline", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(entry.target.getServerInfo().getName())
          );
    }
  }

  /**
   * Returns the position of the given player in the queue.
   *
   * @param player the player to check
   * @return their position in queue, where {@code 1} is first
   * @throws IllegalArgumentException if the player is not queued
   */
  public int getQueuePosition(final UUID player) {
    int position = 1;

    for (ServerQueueEntry entry : queue) {
      if (entry.player.equals(player)) {
        return position;
      }

      position += 1;
    }

    return -1;
  }

  /**
   * Gets all the possible active player instances that are connected to this proxy.
   *
   * @return map of players that are connected to this proxy with its queue entry.
   */
  Map<ServerQueueEntry, UUID> getActivePlayers() {
    Map<ServerQueueEntry, UUID> foundPlayers = new HashMap<>();

    for (ServerQueueEntry entry : queue) {
      foundPlayers.put(entry, entry.player);
    }

    return foundPlayers;
  }

  /**
   * Return the name of the server for this queue.
   *
   * @return The name of the server for this queue.
   */
  public String getServerName() {
    return this.server.getServerInfo().getName();
  }

  public int getSize() {
    return this.queue.size();
  }

  /**
   * Return all the queue entries.
   *
   * @return The queue entries of this queue.
   */
  public List<ServerQueueEntry> getAllEntries() {
    return this.queue.stream().toList();
  }
}
