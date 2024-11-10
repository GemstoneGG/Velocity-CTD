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
import java.util.concurrent.TimeUnit;

/**
 * Manages the queue system.
 */
public class QueueManagerImpl {
  private final VelocityServer server;
  private final VelocityConfiguration.Queue config;
  private ScheduledTask tickMessageTaskHandle;
  private ScheduledTask tickPingingBackendTaskHandle;

  /**
   * Constructs a {@link QueueManagerImpl}.
   *
   * @param server the proxy server
   */
  public QueueManagerImpl(final VelocityServer server) {
    this.server = server;
    config = server.getConfiguration().getQueue();

    if (!config.isEnabled()) {
      return;
    }

    this.schedulePingingBackend();
    this.scheduleTickMessage();
  }

  private void scheduleTickMessage() {
    if (this.tickMessageTaskHandle != null) {
      this.tickMessageTaskHandle.cancel();
    }

    this.tickMessageTaskHandle = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
          for (Player playerApi : this.server.getAllPlayers()) {
            ConnectedPlayer player = (ConnectedPlayer) playerApi;
            player.getQueueStatus().tickMessage();
          }
        })
        .repeat((long) config.getMessageDelay() * 1000, TimeUnit.MILLISECONDS)
        .schedule();
  }

  private void schedulePingingBackend() {
    if (this.tickPingingBackendTaskHandle != null) {
      this.tickPingingBackendTaskHandle.cancel();
    }

    this.tickPingingBackendTaskHandle = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
          for (RegisteredServer serverApi : this.server.getAllServers()) {
            VelocityRegisteredServer server = (VelocityRegisteredServer) serverApi;
            ServerQueueStatus queueStatus = server.getQueueStatus();
            queueStatus.tickPingingBackend();
          }
        })
        .repeat((long) config.getBackendPingInterval() * 1000, TimeUnit.MILLISECONDS)
        .schedule();
  }

  /**
   * Hook that is invoked to reload the server configuration.
   */
  public void reloadConfig() {
    for (RegisteredServer serverApi : this.server.getAllServers()) {
      VelocityRegisteredServer server = (VelocityRegisteredServer) serverApi;
      server.getQueueStatus().reloadConfig();
    }
  }

  /**
   * Hook that removes the player from all queues.
   *
   * @param player the disconnecting player
   */
  public void onPlayerLeave(final ConnectedPlayer player) {
    for (RegisteredServer serverApi : this.server.getAllServers()) {
      VelocityRegisteredServer server = (VelocityRegisteredServer) serverApi;
      server.getQueueStatus().dequeue(player);
    }
  }

  /**
   * Queues the player onto a specific server.
   *
   * @param player the player to queue
   * @param server the server to queue onto
   * @return whether the player queued successfully
   */
  public boolean queueWithIndication(Player player, VelocityRegisteredServer server) {
    return server.getQueueStatus().queueWithIndication(player);
  }
}
