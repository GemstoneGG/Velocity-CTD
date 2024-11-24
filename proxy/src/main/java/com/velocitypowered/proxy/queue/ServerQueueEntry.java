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
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueSendRequest;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.UUID;

/**
 * Stores the status of a single server queue entry for a specific player.
 */
public class ServerQueueEntry {
  public final UUID player;
  public final VelocityRegisteredServer target;
  public final VelocityServer proxy;
  public int connectionAttempts = 0;
  public boolean waitingForConnection = false;
  public int priority;
  public boolean fullBypass;

  /**
   * Constructs a new {@link ServerQueueEntry} instance.
   *
   * @param player the player who is queueing
   * @param target the target server
   */
  public ServerQueueEntry(final UUID player, final VelocityRegisteredServer target,
                          final VelocityServer proxy, final int priority,
                          final boolean fullBypass) {
    this.player = player;
    this.target = target;
    this.proxy = proxy;
    this.priority = priority;
    this.fullBypass = fullBypass;
  }

  /**
   * Executes this queue entry, sending the player to their target server.
   *
   */
  public void send() {
    waitingForConnection = true;

    if (proxy.getMultiProxyHandler().isEnabled()) {
      proxy.getRedisManager().send(new RedisQueueSendRequest(player,
          target.getServerInfo().getName()));
    } else {
      proxy.getPlayer(player).ifPresent(p -> {
        RegisteredServer foundServer = this.proxy.getServer(target.getServerInfo().getName()).orElse(null);

        if (foundServer == null) {
          waitingForConnection = false;
          connectionAttempts += 1;

          if (connectionAttempts == this.proxy.getConfiguration().getQueue().getMaxSendRetries()) {
            proxy.getQueueManager().getQueue(target.getServerInfo().getName()).dequeue(player, true);
          }
        } else {
          if (this.proxy.getConfiguration().getQueue().isForwardKickReason()) {
            p.createConnectionRequest(foundServer).connectWithIndication().thenAccept(result -> {
              if (result) {
                proxy.getQueueManager().getQueue(target.getServerInfo().getName()).dequeue(player, false);
              } else {
                waitingForConnection = false;
                connectionAttempts += 1;

                if (connectionAttempts == this.proxy.getConfiguration().getQueue().getMaxSendRetries()) {
                  proxy.getQueueManager().getQueue(target.getServerInfo().getName()).dequeue(player, true);
                }
              }
            }).exceptionally(ex -> {
              waitingForConnection = false;
              connectionAttempts += 1;

              if (connectionAttempts == this.proxy.getConfiguration().getQueue().getMaxSendRetries()) {
                proxy.getQueueManager().getQueue(target.getServerInfo().getName()).dequeue(player, true);
              }
              return null;
            });
          } else {
            p.createConnectionRequest(foundServer).connect().thenAccept(result -> {
              if (result.isSuccessful()) {
                proxy.getQueueManager().getQueue(target.getServerInfo().getName()).dequeue(player, false);
              } else {
                waitingForConnection = false;
                connectionAttempts += 1;

                if (connectionAttempts == this.proxy.getConfiguration().getQueue().getMaxSendRetries()) {
                  proxy.getQueueManager().getQueue(target.getServerInfo().getName()).dequeue(player, true);
                }
              }
            }).exceptionally(ex -> {
              waitingForConnection = false;
              connectionAttempts += 1;

              if (connectionAttempts == this.proxy.getConfiguration().getQueue().getMaxSendRetries()) {
                proxy.getQueueManager().getQueue(target.getServerInfo().getName()).dequeue(player, true);
              }
              return null;
            });
          }
        }
      });
    }
  }
}
