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
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
  private final Deque<ServerQueueEntry> queue;
  private ServerStatus online = ServerStatus.ONLINE;
  private boolean full = false;
  private boolean paused = false;

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
    queue = new ConcurrentLinkedDeque<>();
    this.reloadConfig();
  }

  /**
   * Constructs a new queue instance.
   *
   * @param server The target server.
   * @param velocityServer The proxy.
   * @param queue The cached queue list.
   * @param online The server status.
   * @param full Full or not.
   * @param paused Paused or not.
   */
  public ServerQueueStatus(final VelocityRegisteredServer server, final VelocityServer velocityServer, final Deque<ServerQueueEntry> queue,
                           final ServerStatus online, final boolean full, final boolean paused) {
    this.server = server;
    this.velocityServer = velocityServer;
    this.queue = queue;
    this.online = online;
    this.full = full;
    this.paused = paused;
    this.reloadConfig();
  }

  /**
   * Returns the whole queue.
   *
   * @return The whole queue.
   */
  public Deque<ServerQueueEntry> getQueue() {
    return this.queue;
  }

  /**
   * Stops the queue.
   */
  public void stop() {
    queue.clear();
    this.velocityServer.getRedisManager().addOrUpdateQueue(this);
  }

  /**
   * Called by {@link QueueManagerRedisImpl} when the proxy config is reloaded.
   */
  void reloadConfig() {
    this.config = this.velocityServer.getConfiguration().getQueue();
  }

  /**
   * Send the first person in the queue.
   */
  public void sendFirstInQueue(final ServerQueueEntry entry) {
    // check if an entry is being sent (this will set to false automatically
    // whether it was successful or not)
    if (entry.isWaitingForConnection()) {
      return;
    }

    entry.send();
  }

  public boolean isOnline() {
    return online == ServerStatus.ONLINE;
  }

  /**
   * Generate the ETA component.
   *
   * @param position pos in queue.
   * @return ETA component.
   */
  public Component calculateEta(final long position) {
    long delayInSeconds = (long) this.config.getSendDelay() * position;

    return QueueTimeFormatter.format(Math.max(delayInSeconds, 0));
  }

  /**
   * Sets whether this queue is paused.
   *
   * @param paused whether this queue is paused
   */
  public void setPaused(final boolean paused) {
    if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
      if (paused) {
        this.velocityServer.getRedisManager().addPausedQueue(getServerName());
      } else {
        this.velocityServer.getRedisManager().removePausedQueue(getServerName());
      }
    } else {
      this.paused = paused;
    }
    this.velocityServer.getRedisManager().addOrUpdateQueue(this);
  }

  /**
   * Queues a player for this server.
   *
   * @param playerUuid the UUID of the player to queue
   * @param priority The priority with which the player should be added.
   */
  public void queue(final UUID playerUuid, final int priority, final boolean fullBypass, final boolean queueBypass) {
    if (!config.isEnabled()) {
      Player player = server.getPlayer(playerUuid);
      if (player != null) {
        player.createConnectionRequest(server).connect();
      } else {
        if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
          this.velocityServer.getRedisManager().send(new RedisQueueSendRequest(playerUuid,
              server.getServerInfo().getName()));
        }
      }
      return;
    }

    ServerQueueEntry entry = new ServerQueueEntry(playerUuid, this.server, this.velocityServer, priority, fullBypass, queueBypass);

    synchronized (queue) {
      var iterator = queue.iterator();
      boolean inserted = false;
      int position = 0;

      while (iterator.hasNext()) {
        ServerQueueEntry currentEntry = iterator.next();

        if (currentEntry.getPriority() < priority) {
          insertAtPosition(entry, position);
          inserted = true;
          break;
        }
        position++;
      }

      if (!inserted) {
        queue.addLast(entry);
      }

      this.velocityServer.getRedisManager().addOrUpdateQueue(this);
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
   * @param maxRetriesReached the maximum number of retries
   */
  public void dequeue(final UUID player, final boolean maxRetriesReached) {
    this.velocityServer.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
      if (maxRetriesReached) {
        if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
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

    this.queue.removeIf(entry -> entry.getPlayer().equals(player));
    this.velocityServer.getRedisManager().addOrUpdateQueue(this);
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
      if (entry.getPlayer().equals(playerUuid)) {
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
    if (this.velocityServer.getQueueManager().isMasterProxy()) {
      return Component.translatable("velocity.queue.command.listqueues.item")
          .arguments(Component.text(server.getServerInfo().getName())
              .hoverEvent(Component.translatable("velocity.queue.command.listqueues.hover")
                  .arguments(
                      Component.text(queue.size()),
                      Component.text(isPaused() ? "True" : "False"),
                      Component.text(isOnline() ? "True" : "False")
                  ).asHoverEvent()
              )
          );
    } else {
      AtomicBoolean status = new AtomicBoolean(true);

      server.ping().whenComplete((result, th)
          -> status.set(th == null)).exceptionally(e -> {
            status.set(false);
            return null;
          }).join();

      return Component.translatable("velocity.queue.command.listqueues.item")
          .arguments(Component.text(server.getServerInfo().getName())
              .hoverEvent(Component.translatable("velocity.queue.command.listqueues.hover")
                  .arguments(
                      Component.text(queue.size()),
                      Component.text(isPaused() ? "True" : "False"),
                      Component.text(status.get() ? "True" : "False")
                  ).asHoverEvent()
              )
          );
    }
  }

  /**
   * Check if the queue is paused.
   *
   * @return Whether the queue is paused or not.
   */
  public boolean isPaused() {
    if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
      return this.velocityServer.getRedisManager().getPausedQueues().contains(getServerName());
    } else {
      return this.paused;
    }
  }

  /**
   * Sends a message in chat to all queued players.
   *
   * @param component the component to send as a message
   */
  public void broadcast(final Component component) {
    for (ServerQueueEntry status : queue) {
      this.velocityServer.getPlayer(status.getPlayer()).ifPresent(player ->
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
      if (queueStatus.getPlayer().equals(playerUuid)) {
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
    int position = getQueuePosition(entry.getPlayer());
    if (entry.isQueueBypass()) {
      return Component.translatable("velocity.queue.player-status.bypass", NamedTextColor.YELLOW);
    } else if (full && !entry.isFullBypass()) {
      return Component.translatable("velocity.queue.player-status.full", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(entry.getTarget().getServerInfo().getName()),
              calculateEta(position)
          );
    } else if (entry.isWaitingForConnection()) {
      return Component.translatable("velocity.queue.player-status.connecting",
          NamedTextColor.YELLOW)
              .arguments(Component.text(entry.getTarget().getServerInfo().getName()));
    } else if (isPaused()) {
      return Component.translatable("velocity.queue.player-status.paused", NamedTextColor.YELLOW);
    } else if (isOnline()) {
      return Component.translatable("velocity.queue.player-status.online", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(entry.getTarget().getServerInfo().getName()),
              calculateEta(position)
          );
    } else {
      return Component.translatable("velocity.queue.player-status.offline", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(entry.getTarget().getServerInfo().getName())
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
      if (entry.getPlayer().equals(player)) {
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
      foundPlayers.put(entry, entry.getPlayer());
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

  /**
   * Get the size of the queue.
   *
   * @return The size of the queue.
   */
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

  /**
   * Gets the status of the queue.
   *
   * @return The status of the queue.
   */
  public ServerStatus getStatus() {
    return this.online;
  }

  /**
   * Checks if the queue is full.
   *
   * @return If the queue is full.
   */
  public boolean isFull() {
    return this.full;
  }

  /**
   * Set the status of the queue.
   *
   * @param serverStatus The queue status.
   */
  public void setStatus(final ServerStatus serverStatus) {
    if (this.online != serverStatus) {
      this.online = serverStatus;
      this.velocityServer.getRedisManager().addOrUpdateQueue(this);
    }
  }

  /**
   * Set the queue full status.
   *
   * @param newFull The full status.
   */
  public void setFull(final boolean newFull) {
    if (this.full != newFull) {
      this.full = newFull;
      this.velocityServer.getRedisManager().addOrUpdateQueue(this);
    }
  }
}
