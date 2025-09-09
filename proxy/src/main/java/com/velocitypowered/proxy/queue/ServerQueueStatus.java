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
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Comparator;
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
   * The capacity for the priority queue.
   */
  private static final int QUEUE_CAPACITY = 10000;

  /**
   * Comparator for ordering queue entries by priority (higher first), then by join time (earlier first).
   * This ensures priority-based ordering with FIFO as a tiebreaker for equal priorities.
   */
  private static final Comparator<ServerQueueEntry> QUEUE_COMPARATOR = (a, b) -> {
    int priorityCompare = Integer.compare(b.getPriority(), a.getPriority());
    if (priorityCompare != 0) {
      return priorityCompare;
    }
    return Long.compare(a.getQueueOrder(), b.getQueueOrder());
  };

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
    this.queue = new PriorityBlockingQueue<>(QUEUE_CAPACITY, QUEUE_COMPARATOR);
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
    this.queue = new PriorityBlockingQueue<>(QUEUE_CAPACITY, QUEUE_COMPARATOR);
    this.playerIndex = new ConcurrentHashMap<>();

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
    Deque<ServerQueueEntry> snapshot = new ConcurrentLinkedDeque<>();
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
    if (entry.isWaitingForConnection()) {
      return;
    }

    boolean removed = queue.remove(entry);
    playerIndex.remove(entry.getPlayer());

    if (removed) {
      logger.debug("Removed queue entry for player {} from server {} queue before sending",
          entry.getPlayer(), getServerName());
    } else {
      logger.warn("Failed to remove queue entry for player {} from server {} queue",
          entry.getPlayer(), getServerName());
    }

    if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
      this.velocityServer.getRedisManager().sendAsync(new RedisQueueRemoveRequest(
          entry.getPlayer(), getServerName(), false))
          .thenRun(() -> logger.debug("Sent RedisQueueRemoveRequest for player {} from server {} after sending",
              entry.getPlayer(), getServerName()))
          .exceptionally(throwable -> {
            logger.error("Failed to send RedisQueueRemoveRequest for player {} from server {} after sending",
                entry.getPlayer(), getServerName(), throwable);
            return null;
          });
    }

    this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
        .exceptionally(throwable -> {
          logger.error("Failed to update queue asynchronously after sending player", throwable);
          return null;
        });

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
        this.velocityServer.getRedisManager().addPausedQueueAsync(getServerName())
            .exceptionally(throwable -> {
              logger.error("Failed to add paused queue asynchronously", throwable);
              return null;
            });
      } else {
        this.velocityServer.getRedisManager().removePausedQueueAsync(getServerName())
            .exceptionally(throwable -> {
              logger.error("Failed to remove paused queue asynchronously", throwable);
              return null;
            });
      }
    } else {
      this.paused = paused;
    }

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
    if (!config.isEnabled()) {
      Player player = server.getPlayer(playerUuid);
      if (player != null) {
        player.createConnectionRequest(server).connect();
      } else {
        if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
          this.velocityServer.getRedisManager().sendAsync(new RedisQueueSendRequest(playerUuid, server.getServerInfo().getName()))
              .thenRun(() -> logger.debug("Sent RedisQueueSendRequest for player {} to server {}", playerUuid, server.getServerInfo().getName()))
              .exceptionally(throwable -> {
                logger.error("Failed to send RedisQueueSendRequest for player {} to server {}", playerUuid, server.getServerInfo().getName(),
                    throwable);
                return null;
              });
        }
      }

      return;
    }

    boolean alreadyInIndex = playerIndex.containsKey(playerUuid);
    logger.debug("Player {} already in index: {}", playerUuid, alreadyInIndex);

    if (alreadyInIndex) {
      logger.debug("Player {} already exists in queue index, skipping", playerUuid);
      return;
    }

    int queueSizeBefore = queue.size();
    int indexSizeBefore = playerIndex.size();
    logger.debug("Queue state before adding player {}: queue size={}, index size={}", playerUuid, queueSizeBefore, indexSizeBefore);

    logger.debug("Attempting to add player {} to priority queue", playerUuid);
    ServerQueueEntry entry = new ServerQueueEntry(playerUuid, this.server, this.velocityServer, priority, fullBypass, queueBypass);
    boolean added = queue.offer(entry);
    logger.debug("Priority queue offer result for player {}: {}", playerUuid, added);

    if (!added) {
      logger.warn("Failed to add player {} to queue for server {}", playerUuid, getServerName());
      return;
    }

    int queueSizeAfterQueue = queue.size();
    logger.debug("Queue state after adding to queue but before index: queue size={}", queueSizeAfterQueue);

    logger.debug("Adding player {} to index after successful queue insertion", playerUuid);
    ServerQueueEntry existingEntry = playerIndex.putIfAbsent(playerUuid, entry);
    if (existingEntry != null) {
      logger.warn("Race condition detected for player {} in queue for server {}, removing duplicate entry", playerUuid, getServerName());
      queue.remove(entry);
      return;
    }

    int queueSizeAfter = queue.size();
    int indexSizeAfter = playerIndex.size();
    logger.debug("Successfully queued player {} to server {} (queue size: {}, index size: {})",
        playerUuid, getServerName(), queueSizeAfter, indexSizeAfter);

    if (queueSizeAfter != queueSizeBefore + 1) {
      logger.warn("Queue size mismatch! Expected: {}, Actual: {}", queueSizeBefore + 1, queueSizeAfter);
    }
    if (indexSizeAfter != indexSizeBefore + 1) {
      logger.warn("Index size mismatch! Expected: {}, Actual: {}", indexSizeBefore + 1, indexSizeAfter);
    }

    this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
        .exceptionally(throwable -> null);

    if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
      String username = getPlayerUsername(playerUuid);

      this.velocityServer.getRedisManager().sendAsync(new RedisQueueAddRequest(
          playerUuid, getServerName(), priority, fullBypass, queueBypass, username))
          .thenRun(() -> logger.debug("Sent RedisQueueAddRequest for player {} to server {}", playerUuid, getServerName()))
          .exceptionally(throwable -> {
            logger.error("Failed to send RedisQueueAddRequest for player {} to server {}", playerUuid, getServerName(), throwable);
            return null;
          });
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

    if (!playerIndex.containsKey(player)) {
      logger.debug("Player {} not found in queue index for server {} - already removed or never queued",
          player, getServerName());
      return;
    }

    this.velocityServer.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
      if (maxRetriesReached) {
        QueueManager.sendMaxRetriesMessage(this.velocityServer, player, getServerName());
      }
    }).delay(1, TimeUnit.SECONDS).schedule();

    ServerQueueEntry removedEntry = playerIndex.remove(player);
    if (removedEntry != null) {
      queue.remove(removedEntry);
      logger.debug("Successfully removed player {} from queue for server {} (queue size: {}, index size: {})",
          player, getServerName(), queue.size(), playerIndex.size());
    } else {
      logger.debug("Player {} not found in queue index for server {} during removal", player, getServerName());
    }

    this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
        .exceptionally(throwable -> {
          logger.error("Failed to update queue asynchronously", throwable);
          return null;
        });

    if (this.velocityServer.getMultiProxyHandler().isRedisEnabled()) {
      this.velocityServer.getRedisManager().sendAsync(new RedisQueueRemoveRequest(
          player, getServerName(), maxRetriesReached))
          .thenRun(() -> logger.debug("Sent RedisQueueRemoveRequest for player {} from server {}", player, getServerName()))
          .exceptionally(throwable -> {
            logger.error("Failed to send RedisQueueRemoveRequest for player {} from server {}", player, getServerName(), throwable);
            return null;
          });
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
      return this.velocityServer.getRedisManager().isQueuePausedCached(getServerName());
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
   * Returns the player index for direct access to player UUIDs.
   * This is used internally for cleanup operations.
   *
   * @return the concurrent player index
   */
  public ConcurrentHashMap<UUID, ServerQueueEntry> getPlayerIndex() {
    return playerIndex;
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

    ServerQueueEntry targetEntry = playerIndex.get(player);
    int position = 1;

    for (ServerQueueEntry entry : queue) {
      if (entry.getPlayer().equals(player)) {
        return position;
      }
      
      int priorityCompare = Integer.compare(entry.getPriority(), targetEntry.getPriority());
      if (priorityCompare > 0 || (priorityCompare == 0 && entry.getQueueOrder() < targetEntry.getQueueOrder())) {
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
      this.velocityServer.getRedisManager().addOrUpdateQueueAsync(this)
          .exceptionally(throwable -> {
            logger.error("Failed to update queue asynchronously", throwable);
            return null;
          });
    }
  }

  /**
   * Gets the username for a player, handling both local and remote players.
   *
   * @param playerUuid the UUID of the player
   * @return the username, or "Unknown" if not found
   */
  private String getPlayerUsername(final UUID playerUuid) {
    if (this.velocityServer.getPlayer(playerUuid).isPresent()) {
      return this.velocityServer.getPlayer(playerUuid).get().getUsername();
    } else {
      var remotePlayer = this.velocityServer.getMultiProxyHandler().getPlayerInfo(playerUuid);
      return remotePlayer != null ? remotePlayer.getUsername() : "Unknown";
    }
  }

  /**
   * Updates the queue from serialized queue data received from another proxy.
   * This method is used for cross-proxy queue synchronization.
   *
   * @param serializableQueue The serialized queue data to update from
   */
  public void updateFromSerializableQueue(final SerializableQueue serializableQueue) {
    logger.debug("Updating queue for server {} from serialized data", getServerName());

    queue.clear();
    playerIndex.clear();

    this.online = serializableQueue.getOnline();
    this.full = serializableQueue.isFull();
    this.paused = serializableQueue.isPaused();

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
            entry.queueBypass(),
            entry.queueOrder()
        );

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
