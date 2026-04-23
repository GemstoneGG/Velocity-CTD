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

import com.velocityctd.api.queue.QueueEntry;
import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.api.queue.ServerStatus;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a player that is currently in a {@link VelocityQueue}.
 */
public class VelocityQueueEntry implements QueueEntry {

  private final UUID uniqueId;
  private final String username;
  protected volatile int priority;
  protected volatile int connectionAttempts;
  protected volatile boolean waitingForConnection;
  protected volatile boolean fullBypass;
  protected volatile boolean queueBypass;

  /**
   * Injected after construction or deserialization.
   */
  protected transient VelocityServer server;

  /**
   * Injected after construction or deserialization.
   */
  private transient VelocityQueue queue;

  /**
   * The 1-based position of this entry in its owning queue.
   * Injected after construction, deserialization, and on position change.
   */
  private transient Integer position;

  /**
   * Creates a new {@link VelocityQueueEntry} from the given data.
   *
   * @param server the proxy server
   * @param queue  the owning queue
   * @param data   the player data
   */
  public VelocityQueueEntry(@NotNull VelocityServer server,
                            @NotNull VelocityQueue queue,
                            @NotNull QueueEntryData data) {
    this.server = server;
    this.queue = queue;
    this.uniqueId = data.uniqueId();
    this.username = data.username();
    this.priority = data.priority();
    this.fullBypass = data.fullBypass();
    this.queueBypass = data.queueBypass();
  }

  /**
   * (Re-)Injects the server and queue context.
   *
   * @param server the proxy server
   * @param queue  the owning queue
   */
  protected void setContext(@NotNull VelocityServer server, @NotNull VelocityQueue queue) {
    this.server = server;
    this.queue = queue;
  }

  /**
   * (Re-)Injects the position index.
   *
   * @param position the position index
   */
  protected void setPosition(int position) {
    this.position = position;
  }

  @Override
  public UUID getUniqueId() {
    return uniqueId;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public int getConnectionAttempts() {
    return connectionAttempts;
  }

  @Override
  public boolean isWaitingForConnection() {
    return waitingForConnection;
  }

  @Override
  public boolean isFullBypass() {
    return fullBypass;
  }

  @Override
  public boolean isQueueBypass() {
    return queueBypass;
  }

  @Override
  public int getPosition() {
    if (position == null) {
      throw new IllegalStateException("Position not set yet.");
    }

    return position;
  }

  @Override
  public VelocityQueue getQueue() {
    return queue;
  }

  /**
   * Initiates the transfer of this player to their target server.
   */
  public void transfer() {
    this.waitingForConnection = true;
    handleTransfer();
  }

  /**
   * Performs the actual connection attempt for this player on the proxy that has them connected.
   * Called directly in local mode, and in response to a VelocityQueueTransfer packet in Redis mode.
   */
  @ApiStatus.Internal
  public void handleTransfer() {
    VelocityConfiguration.Queue config = this.server.getConfiguration().getQueue();
    String targetServerName = this.queue.getName();

    this.server.getPlayer(this.uniqueId).ifPresentOrElse(player -> {
      VelocityRegisteredServer foundServer = this.server.getServer(targetServerName).orElse(null);
      if (foundServer == null) {
        queue.dequeue(this.uniqueId);
        return;
      }

      CompletableFuture<?> future = config.isForwardKickReason()
          ? player.createConnectionRequest(foundServer).connectWithIndication()
          : player.createConnectionRequest(foundServer).connect();

      future.thenAccept(result -> {
        boolean success = false;
        if (result instanceof Boolean b) {
          success = b;
        } else if (result instanceof ConnectionRequestBuilder.Result r) {
          success = r.isSuccessful();
        }

        if (success) {
          queue.dequeue(this.uniqueId);
        } else {
          resetAfterFailedTransfer(config);
        }
      }).exceptionally(ex -> {
        resetAfterFailedTransfer(config);
        return null;
      });
    }, () -> resetAfterFailedTransfer(config));
  }

  /**
   * Aborts a pending transfer that was never picked up.
   *
   * <p>This is a no-op in local mode (only one proxy, transfers are always local).
   * The Redis subclass overrides this to reset the waiting flag and notify other proxies.</p>
   */
  @ApiStatus.Internal
  public void abortTransfer() {
    // no-op
  }

  private void resetAfterFailedTransfer(VelocityConfiguration.Queue config) {
    VelocityRegisteredServer targetServer = this.server.getServer(this.queue.getName()).orElseThrow();

    targetServer.ping().orTimeout(3, TimeUnit.SECONDS).whenComplete((result, th) -> {
      if (th != null) {
        // Backend is offline. Mark it and silently reset this entry without counting the attempt,
        // so the player sees no error and stays in the queue.
        this.queue.setServerStatus(ServerStatus.OFFLINE);
        this.waitingForConnection = false;
        publishWaitingChange();
      } else {
        // Backend is reachable. The connection failure was legitimate.
        applyFailedAttempt(config);
      }
    });
  }

  private void applyFailedAttempt(VelocityConfiguration.Queue config) {
    this.waitingForConnection = false;
    this.connectionAttempts++;
    refreshPermissions();
    publishWaitingChange();

    if (this.connectionAttempts >= config.getMaxSendRetries()) {
      Component message = Component.translatable("velocity.queue.error.max-send-retries-reached")
          .arguments(
              Component.text(this.queue.getName()),
              Component.text(config.getMaxSendRetries()));
      this.server.getScheduler()
          .buildTask(VelocityVirtualPlugin.INSTANCE,
              () -> this.server.getPlayer(this.uniqueId).ifPresent(p -> p.sendMessage(message)))
          .delay(1, TimeUnit.SECONDS)
          .schedule();
      queue.dequeue(this.uniqueId);
    }
  }

  /**
   * Refreshes the player's current permissions into the entry's cached fields.
   */
  protected void refreshPermissions() {
    this.server.getPlayer(this.uniqueId).ifPresent(player -> {
      this.priority = player.getQueuePriority(this.queue.getName());
      this.fullBypass = player.hasPermission("velocity.queue.full.bypass");
      this.queueBypass = player.hasPermission("velocity.queue.bypass");
    });
  }

  /**
   * Hook called whenever {@code waitingForConnection} or the related permission fields change.
   *
   * <p>No-op in local mode. The Redis subclass overrides this to publish a
   * {@code WAITING_CHANGE} sync packet to all other proxies.</p>
   */
  protected void publishWaitingChange() {
    // no-op
  }
}
