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
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Holds queue state for a single backend server.
 */
public class ServerQueueStatus {
  private final VelocityRegisteredServer server;
  private final VelocityConfiguration.Queue config;
  private final Deque<PlayerQueueStatus> queue = new ConcurrentLinkedDeque<>();
  private boolean online = true;
  private boolean paused = false;

  /**
   * Constructs a {@link ServerQueueStatus} instance.
   *
   * @param server the backend server
   * @param velocityServer the proxy server
   */
  public ServerQueueStatus(VelocityRegisteredServer server, VelocityServer velocityServer) {
    this.server = server;
    this.config = velocityServer.getConfiguration().getQueue();
  }

  /**
   * Sends the next player in queue, unless the queue is paused.
   */
  public void tickSending() {
    if (paused || !online) {
      return;
    }

    while (true) {
      PlayerQueueStatus queueStatus = queue.peekFirst();

      if (queueStatus == null) {
        return;
      }

      if (queueStatus.player.isActive()) {
        // if the player is waiting for their send to finish, continue to the next player in queue
        if (!queueStatus.waitingForConnection) {
          queueStatus.send().thenAccept(success -> {
            queueStatus.connectionAttempts++;

            boolean shouldDequeue = success;

            if (queueStatus.connectionAttempts > config.getMaxSendRetries()) {
              queueStatus.player.sendMessage(Component.translatable("velocity.queue.error.max-send-retries-reached").arguments(
                  Component.text(queueStatus.target.getServerInfo().getName()),
                  Component.text(config.getMaxSendRetries())
              ));
              shouldDequeue = true;
            }

            if (shouldDequeue) {
              // if we succeed, or if we exceed the connection attempt limit, remove the player from queue
              queueStatus.dequeue();
              queue.removeFirst();
            }
          });

          break;
        }
      }
    }
  }

  /**
   * Updates the actionbar for all players.
   */
  public void tickMessage() {
    int position = 1;

    for (PlayerQueueStatus queueStatus : queue) {
      Component actionBar;

      if (queueStatus.waitingForConnection) {
        actionBar = Component.translatable("velocity.queue.player-status.connecting", NamedTextColor.YELLOW);
      } else if (paused) {
        actionBar = Component.translatable("velocity.queue.player-status.paused", NamedTextColor.YELLOW);
      } else if (online) {
        actionBar = Component.translatable("velocity.queue.player-status.online", NamedTextColor.YELLOW)
            .arguments(
                Component.text(position),
                Component.text(queue.size()),
                calculateEta(position)
            );
      } else {
        actionBar = Component.translatable("velocity.queue.player-status.offline", NamedTextColor.YELLOW)
            .arguments(
                Component.text(position),
                Component.text(queue.size())
            );
      }

      queueStatus.player.sendActionBar(actionBar);
      position += 1;
    }
  }

  /**
   * Pings the backend to update the online flag.
   */
  public void tickPingingBackend() {
    server.ping().whenComplete((result, th) -> online = th == null);
  }

  private Component calculateEta(int position) {
    int etaSeconds = (int) config.getSendDelay() * position;
    return QueueTimeFormatter.format(etaSeconds);
  }

  /**
   * Sets whether this queue is paused.
   *
   * @param paused whether this queue is paused
   */
  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  /**
   * Queues a player onto this server.
   *
   * @param player the player to queue
   * @return if the player queued successfully
   */
  public boolean queue(Player player) {
    if (!config.isAllowPausedQueueJoining() && paused) {
      return false;
    }

    ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
    this.queue.add(new PlayerQueueStatus(connectedPlayer));
    return true;
  }

  /**
   * Removes a player from this queue.
   *
   * @param player the player to remove
   * @return whether the player was dequeued
   */
  public boolean dequeue(Player player) {
    return this.queue.removeIf(queueStatus -> queueStatus.player.equals(player));
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
  public void broadcast(Component component) {
    for (PlayerQueueStatus status : queue) {
      status.player.sendMessage(component);
    }
  }

  /**
   * Returns whether a player is queued.
   *
   * @param player the player to check
   * @return whether they are queued
   */
  public boolean isQueued(Player player) {
    for (PlayerQueueStatus queueStatus : queue) {
      if (queueStatus.player.equals(player)) {
        return true;
      }
    }

    return false;
  }
}