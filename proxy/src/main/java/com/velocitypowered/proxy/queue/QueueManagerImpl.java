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

import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages the queue system.
 */
public class QueueManagerImpl {
  private final VelocityServer server;
  private final VelocityConfiguration.Queue config;

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

    server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, () -> tickAll(ServerQueueStatus::tickSending))
        .repeat((long) config.getSendDelay() * 1000, TimeUnit.MILLISECONDS)
        .schedule();

    server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, () -> tickAll(ServerQueueStatus::tickMessage))
        .repeat((long) config.getMessageDelay() * 1000, TimeUnit.MILLISECONDS)
        .schedule();

    server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, () -> tickAll(ServerQueueStatus::tickPingingBackend))
        .repeat(1000, TimeUnit.MILLISECONDS)
        .schedule();
  }

  private void tickAll(final Consumer<ServerQueueStatus> consumer) {
    for (RegisteredServer serverApi : this.server.getAllServers()) {
      VelocityRegisteredServer server = (VelocityRegisteredServer) serverApi;
      ServerQueueStatus queueStatus = server.getQueueStatus();
      consumer.accept(queueStatus);
    }
  }
}
