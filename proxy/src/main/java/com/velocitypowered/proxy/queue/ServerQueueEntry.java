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

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendRequest;
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores the status of a single server queue entry for a specific player.
 */
public class ServerQueueEntry {

  /**
   * Logger for queue entry operations.
   */
  private static final Logger logger = LoggerFactory.getLogger(ServerQueueEntry.class);

  /**
   * The UUID of the player associated with this queue entry.
   */
  private final UUID player;

  /**
   * The target server the player is attempting to connect to.
   */
  private final VelocityRegisteredServer target;

  /**
   * The proxy instance managing the queue system.
   */
  private final VelocityServer proxy;

  /**
   * The number of connection attempts made by the player.
   */
  private int connectionAttempts = 0;

  /**
   * Whether the player is currently waiting for a connection attempt.
   */
  private boolean waitingForConnection = false;

  /**
   * The queue priority assigned to the player.
   */
  private int priority;

  /**
   * Whether the player bypasses the full server capacity check.
   */
  private boolean fullBypass;

  /**
   * Whether the player bypasses the queue entirely.
   */
  private boolean queueBypass;

  /**
   * The order in which this entry was added to the queue (for FIFO ordering when priorities are equal).
   */
  private final long queueOrder;

  /**
   * Constructs a new {@link ServerQueueEntry} instance.
   *
   * @param player the UUID of the player in the queue
   * @param target the target server the player is queueing for
   * @param proxy the proxy instance
   * @param priority the queue priority for the player
   * @param fullBypass whether the player bypasses full server limits
   * @param queueBypass whether the player bypasses the queue entirely
   */
  public ServerQueueEntry(final UUID player, final VelocityRegisteredServer target,
                          final VelocityServer proxy, final int priority,
                          final boolean fullBypass,
                          final boolean queueBypass) {
    this.player = player;
    this.target = target;
    this.proxy = proxy;
    this.priority = priority;
    this.fullBypass = fullBypass;
    this.queueBypass = queueBypass;
    this.queueOrder = System.nanoTime();
  }

  /**
   * Constructs a new queue entry.
   *
   * @param player               The UUID of the player.
   * @param target               The target server.
   * @param proxy                The proxy.
   * @param connectionAttempts   The number of connection attempts.
   * @param waitingForConnection Waiting for connection or not.
   * @param priority             The queue priority.
   * @param fullBypass           Full bypass.
   * @param queueBypass          Queue bypass.
   */
  public ServerQueueEntry(final UUID player, final VelocityRegisteredServer target,
                          final VelocityServer proxy, final int connectionAttempts, final boolean waitingForConnection,
                          final int priority, final boolean fullBypass, final boolean queueBypass) {
    this.proxy = proxy;
    this.connectionAttempts = connectionAttempts;
    this.waitingForConnection = waitingForConnection;
    this.priority = priority;
    this.fullBypass = fullBypass;
    this.queueBypass = queueBypass;
    this.target = target;
    this.player = player;
    this.queueOrder = System.nanoTime();
  }

  /**
   * Constructs a new queue entry with a specific queue order (used for deserialization).
   *
   * @param player               The UUID of the player.
   * @param target               The target server.
   * @param proxy                The proxy.
   * @param connectionAttempts   The number of connection attempts.
   * @param waitingForConnection Waiting for connection.
   * @param priority             Priority.
   * @param fullBypass           Full bypass.
   * @param queueBypass          Queue bypass.
   * @param queueOrder           The specific queue order to use.
   */
  public ServerQueueEntry(final UUID player, final VelocityRegisteredServer target,
                          final VelocityServer proxy, final int connectionAttempts, final boolean waitingForConnection,
                          final int priority, final boolean fullBypass, final boolean queueBypass, final long queueOrder) {
    this.proxy = proxy;
    this.connectionAttempts = connectionAttempts;
    this.waitingForConnection = waitingForConnection;
    this.priority = priority;
    this.fullBypass = fullBypass;
    this.queueBypass = queueBypass;
    this.target = target;
    this.player = player;
    this.queueOrder = queueOrder;
  }

  /**
   * Update the entry.
   *
   * @param connectionAttempts   Connection attempts.
   * @param waitingForConnection Waiting for connection.
   * @param priority             Priority.
   * @param fullBypass           Full bypass.
   * @param queueBypass          Queue bypass.
   */
  public void update(final int connectionAttempts, final boolean waitingForConnection,
                     final int priority, final boolean fullBypass, final boolean queueBypass) {
    this.connectionAttempts = connectionAttempts;
    this.waitingForConnection = waitingForConnection;
    this.priority = priority;
    this.fullBypass = fullBypass;
    this.queueBypass = queueBypass;
  }

  /**
   * Executes this queue entry, sending the player to their target server.
   *
   */
  public void send() {
    setWaitingForConnection(true);

    if (proxy.getMultiProxyHandler().isRedisEnabled()) {
      proxy.getRedisManager().sendAsync(new RedisQueueSendRequest(player, target.getServerInfo().getName()))
          .thenRun(() -> logger.debug("Sent RedisQueueSendRequest for player {} to server {}", player, target.getServerInfo().getName()))
          .exceptionally(throwable -> {
            logger.error("Failed to send RedisQueueSendRequest for player {} to server {}", player, target.getServerInfo().getName(), throwable);
            return null;
          });
    } else {
      handleSending();
    }
  }

  /**
   * Transport the player and handle all configuration settings connected to it.
   */
  public void handleSending() {
    proxy.getPlayer(player).ifPresent(p -> {
      RegisteredServer foundServer = this.proxy.getServer(target.getServerInfo().getName()).orElse(null);

      if (foundServer == null) {
        this.proxy.getQueueManager().getQueue(getTarget().getServerInfo().getName()).dequeue(p.getUniqueId(), false);
        return;
      }

      CompletableFuture<?> future;
      if (this.proxy.getConfiguration().getQueue().isForwardKickReason()) {
        future = p.createConnectionRequest(foundServer).connectWithIndication();
      } else {
        future = p.createConnectionRequest(foundServer).connect();
      }

      future.thenAccept(result -> {
        boolean success = false;
        if (result instanceof Boolean b) {
          success = b;
        } else if (result instanceof ConnectionRequestBuilder.Result s) {
          success = s.isSuccessful();
        }

        if (success) {
          logger.debug("Connection successful for player {} to server {} (entry already removed from queue)",
              player, target.getServerInfo().getName());
        } else {
          updateStatus();

          if (getConnectionAttempts() == this.proxy.getConfiguration().getQueue().getMaxSendRetries()) {
            logger.debug("Max retries reached for player {} to server {}", player, target.getServerInfo().getName());
            QueueManager.sendMaxRetriesMessage(this.proxy, player, target.getServerInfo().getName());
          }
        }
      }).exceptionally(ex -> {
        updateStatus();

        if (getConnectionAttempts() == this.proxy.getConfiguration().getQueue().getMaxSendRetries()) {
          logger.debug("Max retries reached (exception) for player {} to server {}", player, target.getServerInfo().getName());
          QueueManager.sendMaxRetriesMessage(this.proxy, player, target.getServerInfo().getName());
        }

        return null;
      });
    });
  }

  /**
   * Get the UUID of the player.
   *
   * @return The UUID of the player.
   */
  public UUID getPlayer() {
    return player;
  }

  /**
   * Get the target server.
   *
   * @return The target server.
   */
  public VelocityRegisteredServer getTarget() {
    return target;
  }

  /**
   * Get the proxy.
   *
   * @return The proxy.
   */
  public VelocityServer getProxy() {
    return proxy;
  }

  /**
   * Get the connection attempts.
   *
   * @return The connection attempts.
   */
  public int getConnectionAttempts() {
    return connectionAttempts;
  }

  /**
   * Gets waiting for connection status.
   *
   * @return The waiting for connection status.
   */
  public boolean isWaitingForConnection() {
    return waitingForConnection;
  }

  /**
   * Sets the waiting for connection status.
   *
   * @param waitingForConnection The waiting for connection status.
   */
  public void setWaitingForConnection(final boolean waitingForConnection) {
    this.waitingForConnection = waitingForConnection;
  }

  /**
   * Update the status after queue sending.
   */
  public void updateStatus() {
    this.waitingForConnection = false;
    this.connectionAttempts++;
    this.proxy.getRedisManager().addOrUpdateEntryAsync(this)
        .exceptionally(throwable -> {
          logger.error("Failed to update entry asynchronously", throwable);
          return null;
        });
  }

  /**
   * Gets the priority.
   *
   * @return The priority.
   */
  public int getPriority() {
    return priority;
  }

  /**
   * Gets the full bypass status.
   *
   * @return {@code true} if this entry bypasses full server checks, {@code false} otherwise
   */
  public boolean isFullBypass() {
    return fullBypass;
  }

  /**
   * Gets the queue bypass mode.
   *
   * @return The queue bypass mode.
   */
  public boolean isQueueBypass() {
    return queueBypass;
  }

  /**
   * Gets the queue order for FIFO ordering when priorities are equal.
   *
   * @return The queue order timestamp.
   */
  public long getQueueOrder() {
    return queueOrder;
  }

  /**
   * Get the username of the player.
   *
   * @return The username of the player, or null if player not found
   */
  public String getUsername() {
    if (this.proxy.getMultiProxyHandler().isRedisEnabled()) {
      RemotePlayerInfo info = this.proxy.getMultiProxyHandler().getPlayerInfo(player);
      if (info == null) {
        this.proxy.getQueueManager().getQueue(this.target.getServerInfo().getName()).dequeue(player, false);
        return null;
      }

      return info.getUsername();
    } else {
      Player p = this.proxy.getPlayer(player).orElse(null);
      if (p == null) {
        this.proxy.getQueueManager().getQueue(this.target.getServerInfo().getName()).dequeue(player, false);
        return null;
      }

      return p.getUsername();
    }
  }
}
