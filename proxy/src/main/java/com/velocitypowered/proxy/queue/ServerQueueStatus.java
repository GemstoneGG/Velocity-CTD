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

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
  private Instant lastSendTime = null;
  private boolean online = true;
  private boolean paused = false;
  private ScheduledTask sendingTaskHandle = null;

  /**
   * Constructs a {@link ServerQueueStatus} instance.
   *
   * @param server the backend server
   * @param velocityServer the proxy server
   */
  public ServerQueueStatus(final VelocityRegisteredServer server, final VelocityServer velocityServer) {
    this.server = server;
    this.velocityServer = velocityServer;
    this.reloadConfig();
  }

  private void rescheduleTimerTask() {
    if (this.sendingTaskHandle != null) {
      this.sendingTaskHandle.cancel();
    }

    this.sendingTaskHandle = this.velocityServer.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::tickSending)
        .repeat((long) this.config.getSendDelay() * 1000, TimeUnit.MILLISECONDS)
        .schedule();
  }

  /**
   * Called by {@link QueueManagerImpl} when the proxy config is reloaded.
   */
  void reloadConfig() {
    this.config = this.velocityServer.getConfiguration().getQueue();
    this.rescheduleTimerTask();
  }

  private void sendFirstInQueue() {
    lastSendTime = Instant.now();
    ServerQueueEntry entry = queue.peekFirst();

    if (entry == null) {
      throw new IllegalStateException("called sendFirstInQueue() with an empty queue");
    }

    if (entry.waitingForConnection) {
      return;
    }

    entry.send().thenAccept(success -> {
      entry.connectionAttempts++;
      boolean shouldDequeue = success;

      if (entry.connectionAttempts > config.getMaxSendRetries()) {
        entry.player.sendMessage(Component.translatable("velocity.queue.error.max-send-retries-reached").arguments(
            Component.text(entry.target.getServerInfo().getName()),
            Component.text(config.getMaxSendRetries())
        ));
        shouldDequeue = true;
      }

      if (shouldDequeue) {
        // if we succeed, or if we exceed the connection attempt limit, remove the player from queue
        queue.removeFirst();
        entry.player.getQueueStatus().queueEntries.removeIf(e -> e.player == entry.player);
      }
    });
  }

  /**
   * Sends the next player in queue, unless the queue is paused.
   */
  private void tickSending() {
    if (paused || !online) {
      return;
    }

    // if there's nobody to send, cancel the task (it being missing will cause the next queue to be sent immediately).
    if (queue.isEmpty()) {
      sendingTaskHandle.cancel();
      sendingTaskHandle = null;
      return;
    }

    while (true) {
      ServerQueueEntry entry = queue.peekFirst();

      if (entry == null) {
        return;
      }

      if (entry.player.isActive()) {
        // if the player is waiting for their send to finish, continue to the next player in queue
        if (!entry.waitingForConnection) {
          sendFirstInQueue();
          break;
        }
      }
    }
  }

  /**
   * Pings the backend to update the online flag.
   */
  public void tickPingingBackend() {
    server.ping().whenComplete((result, th) -> online = th == null);
  }

  private Component calculateEta(final ServerQueueEntry entry, final int position) {
    int etaSeconds = 0;

    // if we already tried to connect, force the ETA to be zero (so it doesn't go up and down).
    if (entry.connectionAttempts == 0) {
      // calculate the time since last send
      Instant now = Instant.now();
      long timeSinceLastSend = this.lastSendTime != null ? this.lastSendTime.until(now, ChronoUnit.SECONDS) : 0;

      // subtract it from the eta
      etaSeconds = (int) config.getSendDelay() * position - (int) timeSinceLastSend;
    }

    return QueueTimeFormatter.format(Math.max(etaSeconds, 0));
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
   * Queues a player onto this server and uses Velocity standard error handling if the connection fails.
   *
   * @param player the player to connect
   * @return whether the player queued successfully
   */
  boolean queueWithIndication(final Player player) {
    if (!config.isEnabled() || player.hasPermission("velocity.queue.bypass." + server.getServerInfo().getName())) {
      player.createConnectionRequest(server).fireAndForget();
      return true;
    }

    final Optional<ServerConnection> currentServer = player.getCurrentServer();

    if (currentServer.isPresent() && currentServer.get().getServer() == server) {
      player.sendMessage(Component.translatable("velocity.error.already-connected"));
      return false;
    }

    if (isQueued(player)) {
      player.sendMessage(Component.translatable("velocity.queue.error.already-queued")
          .arguments(Component.text(server.getServerInfo().getName())));
      return false;
    }

    if (!config.isAllowPausedQueueJoining() && paused && !player.hasPermission("velocity.queue.pause.bypass." + server.getServerInfo().getName())) {
      player.sendMessage(Component.translatable("velocity.queue.error.paused")
          .arguments(Component.text(server.getServerInfo().getName())));
      return false;
    }

    ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;

    ServerQueueEntry entry = new ServerQueueEntry(connectedPlayer, this.server, null);
    this.queue.add(entry);
    connectedPlayer.getQueueStatus().queueEntries.add(entry);

    if (this.sendingTaskHandle == null) {
      sendFirstInQueue();
      this.rescheduleTimerTask();
    }

    player.sendMessage(Component.translatable("velocity.queue.command.queued")
        .arguments(Component.text(server.getServerInfo().getName())));

    return true;
  }

  /**
   * Queues a player onto this server.
   *
   * @param player the player to queue
   * @return a future that will complete once the player connects
   */
  public CompletableFuture<ConnectionRequestBuilder.Result> queue(final Player player) {
    if (!config.isEnabled()) {
      return player.createConnectionRequest(server).connect();
    }

    if (!config.isAllowPausedQueueJoining() && paused) {
      ConnectionRequestBuilder.Result result =
          ConnectionRequestResults.plainResult(ConnectionRequestBuilder.Status.CONNECTION_CANCELLED, server);
      return CompletableFuture.completedFuture(result);
    }

    ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;

    CompletableFuture<ConnectionRequestBuilder.Result> future = new CompletableFuture<>();
    ServerQueueEntry entry = new ServerQueueEntry(connectedPlayer, this.server, future);
    this.queue.add(entry);
    connectedPlayer.getQueueStatus().queueEntries.add(entry);

    if (this.sendingTaskHandle == null) {
      sendFirstInQueue();
      this.rescheduleTimerTask();
    }

    return future;
  }

  /**
   * Removes a player from this queue.
   *
   * @param player the player to remove
   * @return whether the player was dequeued
   */
  public boolean dequeue(final Player player) {
    boolean removedAny = false;

    for (Iterator<ServerQueueEntry> iterator = this.queue.iterator(); iterator.hasNext(); ) {
      ServerQueueEntry entry = iterator.next();

      if (entry.player == player) {
        entry.player.getQueueStatus().queueEntries.removeIf(e -> e.player == entry.player);
        iterator.remove();
        removedAny = true;
      }
    }

    return removedAny;
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
      status.player.sendMessage(component);
    }
  }

  /**
   * Returns whether a player is queued.
   *
   * @param player the player to check
   * @return whether they are queued
   */
  public boolean isQueued(final Player player) {
    for (ServerQueueEntry queueStatus : queue) {
      if (queueStatus.player.equals(player)) {
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

    if (entry.waitingForConnection) {
      return Component.translatable("velocity.queue.player-status.connecting", NamedTextColor.YELLOW)
          .arguments(Component.text(entry.target.getServerInfo().getName()));
    } else if (paused) {
      return Component.translatable("velocity.queue.player-status.paused", NamedTextColor.YELLOW);
    } else if (online) {
      return Component.translatable("velocity.queue.player-status.online", NamedTextColor.YELLOW)
          .arguments(
              Component.text(position),
              Component.text(queue.size()),
              Component.text(entry.target.getServerInfo().getName()),
              calculateEta(entry, position)
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
  int getQueuePosition(final ConnectedPlayer player) {
    int position = 1;

    for (ServerQueueEntry entry : queue) {
      if (entry.player == player) {
        return position;
      }

      position += 1;
    }

    throw new IllegalArgumentException("player " + player.getUsername() + " is not in queue");
  }
}
