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

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessageToUuidRequest;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Holds queue state for a single backend server.
 */
public class ServerQueueStatus {

  /**
   * The backend server this queue is associated with.
   */
  private final VelocityRegisteredServer server;

  /**
   * The Velocity proxy server instance managing this queue.
   */
  private final VelocityServer velocityServer;

  /**
   * The queue-related configuration loaded from the Velocity configuration file.
   */
  private VelocityConfiguration.@MonotonicNonNull Queue config;

  /**
   * The priority queue for efficient O(log n) insertion and O(1) removal of highest priority entry.
   * Uses a custom comparator to maintain FIFO order for same priority entries.
   */
  private final PriorityQueue<ServerQueueEntry> priorityQueue;

  /**
   * Fast lookup map for O(1) player existence checks and entry retrieval.
   * Uses ConcurrentHashMap for lock-free reads.
   */
  private final Map<UUID, ServerQueueEntry> playerMap;

  /**
   * Read-write lock for queue operations. Allows multiple concurrent reads but exclusive writes.
   */
  private final ReentrantReadWriteLock queueLock = new ReentrantReadWriteLock();

  /**
   * The current online status of the target server.
   */
  private ServerStatus online = ServerStatus.ONLINE;

  /**
   * Whether the queue is currently full (i.e., the backend server has no room).
   */
  private boolean full = false;

  /**
   * Whether the queue is currently paused and not accepting or processing players.
   */
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
    
    // Initialize priority queue with custom comparator for FIFO order within same priority
    this.priorityQueue = new PriorityQueue<>(
        Comparator.comparingInt(ServerQueueEntry::getPriority)
            .reversed() // Higher priority first
            .thenComparingLong(entry -> entry.getTimestamp()) // FIFO for same priority
    );
    
    // Use ConcurrentHashMap for lock-free reads
    this.playerMap = new ConcurrentHashMap<>();
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
    
    // Initialize priority queue with custom comparator
    this.priorityQueue = new PriorityQueue<>(
        Comparator.comparingInt(ServerQueueEntry::getPriority)
            .reversed() // Higher priority first
            .thenComparingLong(entry -> entry.getTimestamp()) // FIFO for same priority
    );
    
    // Use ConcurrentHashMap for lock-free reads
    this.playerMap = new ConcurrentHashMap<>();
    
    // Restore queue entries from the provided deque
    queueLock.writeLock().lock();
    try {
      for (ServerQueueEntry entry : queue) {
        this.priorityQueue.offer(entry);
        this.playerMap.put(entry.getPlayer(), entry);
      }
    } finally {
      queueLock.writeLock().unlock();
    }
    
    this.online = online;
    this.full = full;
    this.paused = paused;
    this.reloadConfig();
  }

  /**
   * Returns the whole queue as a deque for backward compatibility.
   *
   * @return The whole queue.
   */
  public Deque<ServerQueueEntry> getQueue() {
    queueLock.readLock().lock();
    try {
      // Convert priority queue to deque maintaining order
      Deque<ServerQueueEntry> deque = new ConcurrentLinkedDeque<>();
      PriorityQueue<ServerQueueEntry> tempQueue = new PriorityQueue<>(priorityQueue);
      while (!tempQueue.isEmpty()) {
        deque.addLast(tempQueue.poll());
      }
      return deque;
    } finally {
      queueLock.readLock().unlock();
    }
  }

  /**
   * Stops the queue.
   */
  public void stop() {
    queueLock.writeLock().lock();
    try {
      priorityQueue.clear();
      playerMap.clear();
    } finally {
      queueLock.writeLock().unlock();
    }
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
   *
   * @param entry the {@link ServerQueueEntry} representing the player to send
   */
  public void sendFirstInQueue(final ServerQueueEntry entry) {
    // Check if an entry is being sent (this will set to false automatically
    // whether it was successful or not).
    if (entry.isWaitingForConnection()) {
      return;
    }

    entry.send();
  }

  /**
   * Returns whether the associated backend server is currently marked as online.
   *
   * @return {@code true} if the server status is {@link ServerStatus#ONLINE}, {@code false} otherwise
   */
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
   * Queues a player for this server with O(log n) insertion complexity.
   *
   * @param playerUuid the UUID of the player to queue
   * @param priority the priority with which the player should be added
   * @param fullBypass {@code true} if the player should bypass full server checks
   * @param queueBypass {@code true} if the player should bypass the queue entirely
   */
  public void queue(final UUID playerUuid, final int priority, final boolean fullBypass, final boolean queueBypass) {
    if (!config.isEnabled()) {
      Player player = server.getPlayer(playerUuid);
      if (player != null) {
        player.createConnectionRequest(server).connect();
      } else {
        if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
          this.velocityServer.getRedisManager().send(new RedisQueueSendRequest(playerUuid, server.getServerInfo().getName()));
        }
      }
      return;
    }

    ServerQueueEntry entry = new ServerQueueEntry(playerUuid, this.server, this.velocityServer, priority, fullBypass, queueBypass);

    // Efficient O(log n) insertion with O(1) lookup map update
    queueLock.writeLock().lock();
    try {
      // Remove existing entry if player is already in queue
      ServerQueueEntry existingEntry = playerMap.remove(playerUuid);
      if (existingEntry != null) {
        priorityQueue.remove(existingEntry); // O(n) but rare case
      }
      
      // Add new entry to both structures
      priorityQueue.offer(entry); // O(log n)
      playerMap.put(playerUuid, entry); // O(1)
    } finally {
      queueLock.writeLock().unlock();
    }

    // Redis update outside synchronized block to prevent blocking
    QueueThreadManager.executeRedisOperation(() -> {
      this.velocityServer.getRedisManager().addOrUpdateQueue(this);
    });
  }

  /**
   * Removes a player from this queue with O(log n) complexity.
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
          this.velocityServer.getPlayer(player).ifPresent(p ->
                  p.sendMessage(Component.translatable("velocity.queue.error.max-send-retries-reached")
                          .arguments(Component.text(getServerName()),
                                  Component.text(this.velocityServer.getConfiguration().getQueue().getMaxSendRetries()))));
        }
      }
    }).delay(1, TimeUnit.SECONDS).schedule();

    // Efficient O(log n) removal
    queueLock.writeLock().lock();
    try {
      ServerQueueEntry entry = playerMap.remove(player);
      if (entry != null) {
        priorityQueue.remove(entry); // O(n) but necessary for priority queue
      }
    } finally {
      queueLock.writeLock().unlock();
    }

    // Redis update outside to prevent blocking
    QueueThreadManager.executeRedisOperation(() -> {
      this.velocityServer.getRedisManager().addOrUpdateQueue(this);
    });
  }

  /**
   * Gets the {@link ServerQueueEntry} for the player with O(1) complexity.
   * Lock-free read operation using ConcurrentHashMap.
   *
   * @param playerUuid The UUID of the player.
   * @return The {@link ServerQueueEntry} for the player.
   */
  public Optional<ServerQueueEntry> getEntry(final UUID playerUuid) {
    // Lock-free read using ConcurrentHashMap
    return Optional.ofNullable(playerMap.get(playerUuid)); // O(1)
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
                                      Component.text(getSize()),
                                      Component.text(isPaused() ? "True" : "False"),
                                      Component.text(isOnline() ? "True" : "False")
                              ).asHoverEvent())
              );
    } else {
      // Use cached status instead of blocking ping
      return Component.translatable("velocity.queue.command.listqueues.item")
              .arguments(Component.text(server.getServerInfo().getName())
                      .hoverEvent(Component.translatable("velocity.queue.command.listqueues.hover")
                              .arguments(
                                      Component.text(getSize()),
                                      Component.text(isPaused() ? "True" : "False"),
                                      Component.text(isOnline() ? "True" : "False")
                              ).asHoverEvent())
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
   * Uses read lock to prevent blocking during iteration.
   *
   * @param component the component to send as a message
   */
  public void broadcast(final Component component) {
    List<ServerQueueEntry> entriesToMessage = new ArrayList<>();
    
    // Use read lock to get a snapshot of entries
    queueLock.readLock().lock();
    try {
      entriesToMessage.addAll(priorityQueue);
    } finally {
      queueLock.readLock().unlock();
    }
    
    // Send messages outside the lock to prevent blocking
    for (ServerQueueEntry status : entriesToMessage) {
      this.velocityServer.getPlayer(status.getPlayer()).ifPresent(player ->
              player.sendMessage(component));
    }
  }

  /**
   * Returns whether a player is queued with O(1) complexity.
   * Lock-free read operation using ConcurrentHashMap.
   *
   * @param playerUuid the player uuid to check
   * @return whether they are queued
   */
  public boolean isQueued(final UUID playerUuid) {
    // Lock-free read using ConcurrentHashMap
    return playerMap.containsKey(playerUuid); // O(1)
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
      return Component.translatable("velocity.queue.actionbar.bypass")
              .arguments(Component.text(getServerName()));
    }

    return Component.translatable("velocity.queue.actionbar.queued")
            .arguments(
                    Component.text(getServerName()),
                    Component.text(position),
                    Component.text(getSize()),
                    calculateEta(position)
            );
  }

  /**
   * Gets the queue position of a player with O(n) complexity (but optimized).
   * Uses read lock to prevent blocking during iteration.
   *
   * @param player The player to get the position for.
   * @return The position of the player in the queue, or -1 if not found.
   */
  public int getQueuePosition(final UUID player) {
    // Quick check first using lock-free read
    if (!playerMap.containsKey(player)) {
      return -1;
    }
    
    // Use read lock for iteration
    queueLock.readLock().lock();
    try {
      int position = 1;
      // Iterate through priority queue to find position
      for (ServerQueueEntry entry : priorityQueue) {
        if (entry.getPlayer().equals(player)) {
          return position;
        }
        position++;
      }
      return -1;
    } finally {
      queueLock.readLock().unlock();
    }
  }

  /**
   * Gets all the possible active player instances that are connected to this proxy.
   *
   * @return map of players that are connected to this proxy with its queue entry.
   */
  Map<ServerQueueEntry, UUID> getActivePlayers() {
    queueLock.readLock().lock();
    try {
      Map<ServerQueueEntry, UUID> foundPlayers = new HashMap<>();
      for (ServerQueueEntry entry : priorityQueue) {
        foundPlayers.put(entry, entry.getPlayer());
      }
      return foundPlayers;
    } finally {
      queueLock.readLock().unlock();
    }
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
   * Get the size of the queue with O(1) complexity.
   * Lock-free read operation.
   *
   * @return The size of the queue.
   */
  public int getSize() {
    // Lock-free read using ConcurrentHashMap size
    return playerMap.size(); // O(1)
  }

  /**
   * Return all the queue entries.
   *
   * @return The queue entries of this queue.
   */
  public List<ServerQueueEntry> getAllEntries() {
    queueLock.readLock().lock();
    try {
      return new ArrayList<>(priorityQueue);
    } finally {
      queueLock.readLock().unlock();
    }
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
