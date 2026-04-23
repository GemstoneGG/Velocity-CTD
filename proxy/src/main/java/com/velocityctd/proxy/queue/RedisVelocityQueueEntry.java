/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

package com.velocityctd.proxy.queue;

import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.proxy.queue.redis.packet.VelocityQueueSync;
import com.velocityctd.proxy.queue.redis.packet.VelocityQueueTransfer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Redis-aware extension of {@link VelocityQueueEntry}.
 */
public final class RedisVelocityQueueEntry extends VelocityQueueEntry {

  /**
   * Creates a new {@link RedisVelocityQueueEntry}.
   *
   * @param server the proxy server
   * @param queue  the owning queue
   * @param data   the player data
   */
  public RedisVelocityQueueEntry(@NotNull VelocityServer server,
                                 @NotNull RedisVelocityQueue queue,
                                 @NotNull QueueEntryData data) {
    super(server, queue, data);
  }

  /**
   * Initiates a cross-proxy transfer by publishing a {@link VelocityQueueTransfer} packet.
   *
   * <p>The proxy that currently holds the player will receive the packet and call
   * {@link #handleTransfer()} locally. A timeout task is scheduled to call
   * {@link #abortTransfer()} if no proxy responds in time.</p>
   */
  @Override
  public void transfer() {
    this.waitingForConnection = true;
    publishWaitingChange();

    server.getRedis().publish(new VelocityQueueTransfer(getUniqueId(), getQueue().getName()));

    this.server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::abortTransfer)
        .delay(this.server.getConfiguration().getReadTimeout(), TimeUnit.MILLISECONDS)
        .schedule();
  }

  /**
   * Resets the waiting state after a transfer was never picked up by any proxy.
   *
   * <p>Checks the volatile {@code waitingForConnection} flag first. If another proxy already
   * handled the transfer and published a {@code WAITING_CHANGE} sync that reset the flag,
   * the abort is skipped.</p>
   */
  @Override
  @ApiStatus.Internal
  public void abortTransfer() {
    if (!this.waitingForConnection) {
      return;
    }

    this.waitingForConnection = false;
    this.connectionAttempts++;
    refreshPermissions();
    publishWaitingChange();
  }

  /**
   * Publishes a {@code WAITING_CHANGE} sync packet so all proxies update their copy of
   * this entry's mutable state.
   */
  @Override
  protected void publishWaitingChange() {
    server.getRedis().publish(VelocityQueueSync.waitingChange(
        getQueue().getName(), getUniqueId(), this.waitingForConnection,
        this.connectionAttempts, this.priority, this.fullBypass, this.queueBypass
    ));
  }

  /**
   * Updates this entry's mutable fields from a {@code WAITING_CHANGE} sync packet received
   * from another proxy.
   *
   * @param waiting            the new waitingForConnection value
   * @param attempts           the new connectionAttempts value
   * @param updatedPriority    the refreshed priority
   * @param updatedFullBypass  the refreshed fullBypass flag
   * @param updatedQueueBypass the refreshed queueBypass flag
   */
  @ApiStatus.Internal
  public void applyWaitingChangeFromPacket(boolean waiting, int attempts,
                                           int updatedPriority,
                                           boolean updatedFullBypass,
                                           boolean updatedQueueBypass) {
    this.waitingForConnection = waiting;
    this.connectionAttempts = attempts;
    this.priority = updatedPriority;
    this.fullBypass = updatedFullBypass;
    this.queueBypass = updatedQueueBypass;
  }
}
