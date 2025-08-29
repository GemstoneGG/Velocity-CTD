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

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.queue.cache.SerializableQueue;
import com.velocitypowered.proxy.queue.cache.SerializableQueueEntry;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueAddRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueRemoveRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisSendMessageToUuidRequest;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds queue state for a single backend server.
 */
public class ServerQueueStatus {

  /**
   * The logger used for reporting queue activity, debug output,
   * and error conditions within {@link ServerQueueStatus}.
   */
  private static final Logger logger = LoggerFactory.getLogger(ServerQueueStatus.class);

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
   * The collection of {@link ServerQueueEntry} representing the current queue.
   * Using PriorityBlockingQueue for thread-safe priority-based ordering.
   */
  private final PriorityBlockingQueue<ServerQueueEntry> queue;

  /**
   * Concurrent index for O(1) player lookups to prevent race conditions.
   */
  private final ConcurrentHashMap<UUID, ServerQueueEntry> playerIndex;

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
    // Use PriorityBlockingQueue with custom comparator for thread-safe priority ordering
    // Compare by priority first (higher priority first), then by UUID for consistent ordering
    this.queue = new PriorityBlockingQueue<>(100, (a, b) -> {
      int priorityCompare = Integer.compare(b.getPriority(), a.getPriority());
      logger.debug("Comparator called: {} (priority {}) vs {} (priority {}), priorityCompare: {}",
          a.getPlayer(), a.getPriority(), b.getPlayer(), b.getPriority(), priorityCompare);
      if (priorityCompare != 0) {
        return priorityCompare;
      }
      // If priorities are equal, use UUID for consistent ordering
      int uuidCompare = a.getPlayer().compareTo(b.getPlayer());
      logger.debug("UUID comparison for equal priorities: {} vs {}, result: {}",
          a.getPlayer(), b.getPlayer(), uuidCompare);
      return uuidCompare;
    });
    this.playerIndex = new ConcurrentHashMap<>();
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
    // Convert existing deque to PriorityBlockingQueue for thread safety
    // Compare by priority first (higher priority first), then by UUID for consistent ordering
    this.queue = new PriorityBlockingQueue<>(100, (a, b) -> {
      int priorityCompare = Integer.compare(b.getPriority(), a.getPriority());
      logger.debug("Comparator called: {} (priority {}) vs {} (priority {}), priorityCompare: {}",
          a.getPlayer(), a.getPriority(), b.getPlayer(), b.getPriority(), priorityCompare);
      if (priorityCompare != 0) {
        return priorityCompare;
      }
      // If priorities are equal, use UUID for consistent ordering
      int uuidCompare = a.getPlayer().compareTo(b.getPlayer());
      logger.debug("UUID comparison for equal priorities: {} vs {}, result: {}",
          a.getPlayer(), b.getPlayer(), uuidCompare);
      return uuidCompare;
    });
    this.playerIndex = new ConcurrentHashMap<>();

    // Populate the new queue and index from the existing deque
    for (ServerQueueEntry entry : queue) {
      this.queue.offer(entry);
      this.playerIndex.put(entry.getPlayer(), entry);
    }

    this.online = online;
    this.full = full;
    this.paused = paused;
    this.reloadConfig();
  }

  /**
   * Returns the whole queue as a deque for backward compatibility.
   * Note: This creates a snapshot and may not reflect real-time changes.
   *
   * @return The whole queue as a deque.
   */
  public Deque<ServerQueueEntry> getQueue() {
    // Create a snapshot of the current queue state for backward compatibility
    Deque<ServerQueueEntry> snapshot = new ConcurrentLinkedDeque<>();
    // Use toArray to avoid concurrent modification issues
    Object[] entries = queue.toArray();
    for (Object entry : entries) {
      if (entry instanceof ServerQueueEntry) {
        snapshot.addLast((ServerQueueEntry) entry);
      }
    }
    return snapshot;
  }

  /**
   * Stops the queue.
   */
  public void stop() {
    queue.clear();
    playerIndex.clear();
    // Use async Redis operation to avoid blocking
    this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
        .exceptionally(throwable -> {
          logger.error("Failed to update queue asynchronously", throwable);
          return null;
        });
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

    // Remove from queue before sending to prevent race conditions
    // This ensures consistent behavior between Redis and non-Redis scenarios
    boolean removed = queue.remove(entry);
    playerIndex.remove(entry.getPlayer());

    if (removed) {
      logger.debug("Removed queue entry for player {} from server {} queue before sending",
          entry.getPlayer(), getServerName());
    } else {
      logger.warn("Failed to remove queue entry for player {} from server {} queue",
          entry.getPlayer(), getServerName());
    }

    // Now send the player
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

    // Use async Redis operation to avoid blocking
    this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
        .exceptionally(throwable -> {
          logger.error("Failed to update queue asynchronously", throwable);
          return null;
        });
  }

  /**
   * Queues a player for this server.
   * Thread-safe implementation using PriorityBlockingQueue and concurrent index.
   *
   * @param playerUuid the UUID of the player to queue
   * @param priority the priority with which the player should be added
   * @param fullBypass {@code true} if the player should bypass full server checks
   * @param queueBypass {@code true} if the player should bypass the queue entirely
   */
  public void queue(final UUID playerUuid, final int priority, final boolean fullBypass, final boolean queueBypass) {
    logger.debug("Queue operation started for player {} on server {} with priority {}", playerUuid, getServerName(), priority);

    if (!config.isEnabled()) {
      logger.debug("Queue system disabled for server {}, connecting player {} directly", getServerName(), playerUuid);
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

    // Thread-safe queue insertion using atomic operations
    // First, check if player is already in the queue (not just the index)
    boolean alreadyInIndex = playerIndex.containsKey(playerUuid);
    logger.debug("Player {} already in index: {}", playerUuid, alreadyInIndex);

    if (alreadyInIndex) {
      // Player already exists in queue, don't add duplicate
      logger.debug("Player {} already exists in queue index, skipping", playerUuid);
      return;
    }

    // Log queue state before adding
    int queueSizeBefore = queue.size();
    int indexSizeBefore = playerIndex.size();
    logger.debug("Queue state before adding player {}: queue size={}, index size={}", playerUuid, queueSizeBefore, indexSizeBefore);
    dumpQueueContents("before adding " + playerUuid);

    // Add to priority queue first - this is thread-safe and maintains priority order
    logger.debug("Attempting to add player {} to priority queue", playerUuid);
    ServerQueueEntry entry = new ServerQueueEntry(playerUuid, this.server, this.velocityServer, priority, fullBypass, queueBypass);
    boolean added = queue.offer(entry);
    logger.debug("Priority queue offer result for player {}: {}", playerUuid, added);

    if (!added) {
      logger.warn("Failed to add player {} to queue for server {}", playerUuid, getServerName());
      return;
    }

    // Log queue state after adding to queue but before adding to index
    int queueSizeAfterQueue = queue.size();
    logger.debug("Queue state after adding to queue but before index: queue size={}", queueSizeAfterQueue);
    dumpQueueContents("after adding to queue " + playerUuid);

    // Only add to index after successful queue insertion to prevent race conditions
    logger.debug("Adding player {} to index after successful queue insertion", playerUuid);
    ServerQueueEntry existingEntry = playerIndex.putIfAbsent(playerUuid, entry);
    if (existingEntry != null) {
      // Another thread added the player while we were processing, remove our entry
      logger.warn("Race condition detected for player {} in queue for server {}, removing duplicate entry", playerUuid, getServerName());
      queue.remove(entry);
      return;
    }

    // Log final queue state
    int queueSizeAfter = queue.size();
    int indexSizeAfter = playerIndex.size();
    logger.debug("Successfully queued player {} to server {} (queue size: {}, index size: {})",
        playerUuid, getServerName(), queueSizeAfter, indexSizeAfter);
    dumpQueueContents("after adding to index " + playerUuid);

    // Additional verification
    if (queueSizeAfter != queueSizeBefore + 1) {
      logger.warn("Queue size mismatch! Expected: {}, Actual: {}", queueSizeBefore + 1, queueSizeAfter);
    }
    if (indexSizeAfter != indexSizeBefore + 1) {
      logger.warn("Index size mismatch! Expected: {}, Actual: {}", indexSizeBefore + 1, indexSizeAfter);
    }

    // Use async Redis operation to avoid blocking
    this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
        .exceptionally(throwable -> {
          // logger.error("Failed to update queue asynchronously", throwable); // Original code had this line commented out
          return null;
        });

    // Send Redis packet to notify other proxies about the queue addition
    if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
      try {
        // Get player username for the packet
        String username = "Unknown";
        if (this.velocityServer.getPlayer(playerUuid).isPresent()) {
          username = this.velocityServer.getPlayer(playerUuid).get().getUsername();
        } else {
          // Try to get from remote player info
          var remotePlayer = this.velocityServer.getMultiProxyHandler().getPlayerInfo(playerUuid);
          if (remotePlayer != null) {
            username = remotePlayer.getUsername();
          }
        }

        this.velocityServer.getRedisManager().send(new RedisQueueAddRequest(
            playerUuid, getServerName(), priority, fullBypass, queueBypass, username));
        logger.debug("Sent RedisQueueAddRequest for player {} to server {}", playerUuid, getServerName());
      } catch (Exception e) {
        logger.error("Failed to send RedisQueueAddRequest for player {} to server {}", playerUuid, getServerName(), e);
      }
    }
  }

  /**
   * Removes a player from this queue.
   * Thread-safe implementation using atomic operations.
   *
   * @param player the player to remove
   * @param maxRetriesReached the maximum number of retries
   */
  public void dequeue(final UUID player, final boolean maxRetriesReached) {
    logger.debug("Dequeue operation started for player {} on server {} (maxRetriesReached: {})",
        player, getServerName(), maxRetriesReached);

    // Check if player is actually in the queue before attempting to dequeue
    if (!playerIndex.containsKey(player)) {
      logger.debug("Player {} not found in queue index for server {} - already removed or never queued", 
          player, getServerName());
      return;
    }

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

    // Thread-safe removal using atomic operations
    ServerQueueEntry removedEntry = playerIndex.remove(player);
    if (removedEntry != null) {
      // Remove from priority queue - this is thread-safe
      queue.remove(removedEntry);
      logger.debug("Successfully removed player {} from queue for server {} (queue size: {}, index size: {})",
          player, getServerName(), queue.size(), playerIndex.size());
    } else {
      // This should rarely happen now due to the check above, but keep as safety net
      logger.debug("Player {} not found in queue index for server {} during removal", player, getServerName());
    }

    // Use async Redis operation to avoid blocking
    this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
        .exceptionally(throwable -> {
          logger.error("Failed to update queue asynchronously", throwable);
          return null;
        });

    // Send Redis packet to notify other proxies about the queue removal
    if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
      try {
        this.velocityServer.getRedisManager().send(new RedisQueueRemoveRequest(
            player, getServerName(), maxRetriesReached));
        logger.debug("Sent RedisQueueRemoveRequest for player {} from server {}", player, getServerName());
      } catch (Exception e) {
        logger.error("Failed to send RedisQueueRemoveRequest for player {} from server {}", player, getServerName(), e);
      }
    }
  }

  /**
   * Gets the {@link ServerQueueEntry} for the player.
   * Thread-safe O(1) lookup using concurrent index.
   *
   * @param playerUuid The UUID of the player.
   *
   * @return The {@link ServerQueueEntry} for the player.
   */
  public Optional<ServerQueueEntry> getEntry(final UUID playerUuid) {
    ServerQueueEntry entry = playerIndex.get(playerUuid);
    return Optional.ofNullable(entry);
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
                  ).asHoverEvent())
          );
    } else {
      // Use cached status for non-master proxies to avoid blocking
      // The status will be updated asynchronously by the master proxy
      boolean serverStatus = this.online == ServerStatus.ONLINE;

      return Component.translatable("velocity.queue.command.listqueues.item")
          .arguments(Component.text(server.getServerInfo().getName())
              .hoverEvent(Component.translatable("velocity.queue.command.listqueues.hover")
                  .arguments(
                      Component.text(queue.size()),
                      Component.text(isPaused() ? "True" : "False"),
                      Component.text(serverStatus ? "True" : "False")
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
   *
   * @param component the component to send as a message
   */
  public void broadcast(final Component component) {
    // Use playerIndex for thread-safe iteration
    for (ServerQueueEntry entry : playerIndex.values()) {
      this.velocityServer.getPlayer(entry.getPlayer()).ifPresent(player ->
          player.sendMessage(component));
    }
  }

  /**
   * Returns whether a player is queued.
   * Thread-safe O(1) lookup using concurrent index.
   *
   * @param playerUuid the player uuid to check
   * @return whether they are queued
   */
  public boolean isQueued(final UUID playerUuid) {
    boolean queued = playerIndex.containsKey(playerUuid);
    logger.debug("Checking if player {} is queued on server {}: {}", playerUuid, getServerName(), queued);
    return queued;
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
   * Note: This is now O(n) as we need to iterate through the priority queue,
   * but it's only used for display purposes and not in critical paths.
   *
   * @param player the player to check
   * @return their position in queue, where {@code 1} is first
   * @throws IllegalArgumentException if the player is not queued
   */
  public int getQueuePosition(final UUID player) {
    if (!playerIndex.containsKey(player)) {
      return -1;
    }

    int position = 1;
    // Create a snapshot of the queue for position calculation
    Object[] entries = queue.toArray();

    for (Object obj : entries) {
      if (obj instanceof ServerQueueEntry entry) {
        if (entry.getPlayer().equals(player)) {
          return position;
        }
        position++;
      }
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

    // Use playerIndex for thread-safe iteration
    for (Map.Entry<UUID, ServerQueueEntry> entry : playerIndex.entrySet()) {
      foundPlayers.put(entry.getValue(), entry.getKey());
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
    // Return a snapshot of all entries from the player index
    return List.copyOf(playerIndex.values());
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
      // Use async Redis operation to avoid blocking
      this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
          .exceptionally(throwable -> {
            logger.error("Failed to update queue asynchronously", throwable);
            return null;
          });
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
      // Use async Redis operation to avoid blocking
      this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
          .exceptionally(throwable -> {
            logger.error("Failed to update queue asynchronously", throwable);
            return null;
          });
    }
  }

  /**
   * Debug method to dump current queue contents.
   *
   * @param operation a descriptive label of the operation that triggered the dump
   */
  private void dumpQueueContents(final String operation) {
    logger.debug("=== Queue dump after {} ===", operation);
    logger.debug("Queue size: {}, Index size: {}", queue.size(), playerIndex.size());

    // Dump queue contents
    Object[] queueArray = queue.toArray();
    logger.debug("Queue contents ({} items):", queueArray.length);
    for (int i = 0; i < queueArray.length; i++) {
      if (queueArray[i] instanceof ServerQueueEntry entry) {
        logger.debug("  [{}] Player: {}, Priority: {}", i, entry.getPlayer(), entry.getPriority());
      }
    }

    // Dump index contents
    logger.debug("Index contents ({} items):", playerIndex.size());
    for (Map.Entry<UUID, ServerQueueEntry> entry : playerIndex.entrySet()) {
      logger.debug("  Player: {}, Priority: {}", entry.getKey(), entry.getValue().getPriority());
    }
    logger.debug("=== End queue dump ===");
  }

  /**
   * Updates the queue from serialized queue data received from another proxy.
   * This method is used for cross-proxy queue synchronization.
   *
   * @param serializableQueue The serialized queue data to update from
   */
  public void updateFromSerializableQueue(final SerializableQueue serializableQueue) {
    logger.debug("Updating queue for server {} from serialized data", getServerName());

    // Clear current queue and index
    queue.clear();
    playerIndex.clear();

    // Update server status
    this.online = serializableQueue.getOnline();
    this.full = serializableQueue.isFull();
    this.paused = serializableQueue.isPaused();

    // Add all entries from the serialized data
    for (SerializableQueueEntry entry : serializableQueue.getQueue()) {
      try {
        ServerQueueEntry queueEntry = new ServerQueueEntry(
            entry.uuid(),
            this.server,
            this.velocityServer,
            entry.connectionAttempts(),
            entry.waitingForConnection(),
            entry.priority(),
            entry.fullBypass(),
            entry.queueBypass()
        );

        // Add to both queue and index
        queue.offer(queueEntry);
        playerIndex.put(entry.uuid(), queueEntry);

        logger.debug("Added player {} to queue from serialized data with priority {}", 
            entry.uuid(), entry.priority());
      } catch (Exception e) {
        logger.error("Error adding player {} to queue from serialized data", entry.uuid(), e);
      }
    }

    logger.debug("Updated queue for server {} with {} entries", getServerName(), queue.size());
  }
}
