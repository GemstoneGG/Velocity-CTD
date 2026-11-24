/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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
import com.velocityctd.proxy.util.ComponentUtils;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a player that is currently in a {@link VelocityQueue}.
 */
public class VelocityQueueEntry implements QueueEntry {

  private final UUID uniqueId;
  private final String username;

  // All mutable fields below are guarded by synchronization on this entry instance.
  // Reads and writes must occur inside a synchronized(this) block (or a synchronized method).
  protected int priority;
  protected int connectionAttempts;
  protected boolean waitingForConnection;
  protected boolean fullBypass;
  protected boolean queueBypass;

  /**
   * Epoch-millisecond timestamp when this entry was created, i.e. when the player joined the
   * queue. 0 when restored from a depot snapshot that predates this field.
   */
  protected long joinedAtMs = System.currentTimeMillis();

  /**
   * Epoch-millisecond timestamp when this player disconnected while queued, or 0 if the
   * player is currently online or if the disconnect was not recorded (e.g. force-kill).
   */
  protected long offlineSinceMs = 0;

  /**
   * The timeout in seconds that was active at the time of disconnect or 0 if unknown.
   * Only meaningful when {@link #offlineSinceMs} is non-zero.
   */
  protected int offlineTimeoutSeconds = 0;

  /**
   * Injected after construction or deserialization.
   */
  protected transient VelocityServer server;

  /**
   * Injected after construction or deserialization.
   */
  private transient VelocityQueue<?> queue;

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
                            @NotNull VelocityQueue<?> queue,
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
  protected void setContext(@NotNull VelocityServer server, @NotNull VelocityQueue<?> queue) {
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
  public synchronized int getPriority() {
    return priority;
  }

  public long getJoinedAtMs() {
    return joinedAtMs;
  }

  @Override
  public synchronized int getConnectionAttempts() {
    return connectionAttempts;
  }

  @Override
  public synchronized boolean isWaitingForConnection() {
    return waitingForConnection;
  }

  @Override
  public synchronized boolean isFullBypass() {
    return fullBypass;
  }

  @Override
  public synchronized boolean isQueueBypass() {
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
  public VelocityQueue<?> getQueue() {
    return queue;
  }

  /**
   * Initiates the transfer of this player to their target server.
   */
  public void transfer() {
    synchronized (this) {
      this.waitingForConnection = true;
    }
    handleTransfer();
  }

  /**
   * Performs the actual connection attempt for this player on the proxy that has them connected.
   * Called directly in local mode, and in response to a VelocityQueueTransfer packet in Redis mode.
   */
  @ApiStatus.Internal
  public void handleTransfer() {
    VelocityConfiguration.Queue config = this.server.getConfiguration().getQueue();

    this.server.getPlayer(this.uniqueId).ifPresentOrElse(player -> {
      // Resolve through the entry so full-bypass reaches the resolver, and remember the
      // exact backend we tried: re-resolving on failure would advance round-robin/random
      // and could diagnose a different backend than the one that refused the player.
      VelocityRegisteredServer foundServer = this.queue.resolveTargetServer(this).orElse(null);
      if (foundServer == null) {
        // Temporary unavailability (group full/offline). Keep queue position, priority and
        // attempts untouched; the transfer tick interval is the natural retry backoff.
        resetWaitingWithoutAttempt();
        return;
      }

      player.createConnectionRequest(foundServer).connect().thenAccept(result -> {
        if (result.isSuccessful()) {
          queue.dequeue(this.uniqueId);
          return;
        }

        player.handleServerDisconnectResult(foundServer, result);

        Component reason = result.getReasonComponent().orElse(null);
        if (reason != null) {
          for (String banned : config.getBannedReason()) {
            if (ComponentUtils.containsString(reason, banned)) {
              queue.dequeue(this.uniqueId);
              return;
            }
          }
        }

        resetAfterFailedTransfer(config, foundServer);
      }).exceptionally(ex -> {
        resetAfterFailedTransfer(config, foundServer);
        return null;
      });
    }, () -> resetAfterFailedTransfer(config,
        this.queue.isGroupQueue() ? null : this.queue.getServer()));
  }

  /**
   * Aborts a pending transfer which was never picked up.
   *
   * <p>This is a no-op in local mode (only one proxy, transfers are always local).
   * The Redis subclass overrides this to reset the waiting flag and notify other proxies.</p>
   */
  @ApiStatus.Internal
  public void abortTransfer() {
    // no-op
  }

  /**
   * Diagnoses a failed connection against the backend that was actually attempted, so a
   * round-robin or random strategy is not advanced a second time during the diagnosis.
   *
   * @param config          the queue configuration
   * @param attemptedServer the backend tried by {@link #handleTransfer()}, or {@code null}
   *                        when no connection was made (offline player / no target)
   */
  private void resetAfterFailedTransfer(VelocityConfiguration.Queue config,
                                        @Nullable VelocityRegisteredServer attemptedServer) {
    if (attemptedServer == null) {
      // No connection was ever made, so there is nothing to penalise.
      resetWaitingWithoutAttempt();
      return;
    }

    attemptedServer.ping().orTimeout(3, TimeUnit.SECONDS).whenComplete((result, th) -> {
      if (th != null) {
        // A physical backend failure marks its own queue offline. A group has several backends
        // and its aggregate status is owned by ServerGroupManager.refresh(), so one failed
        // member must not stall transfers to the healthy members.
        if (!this.queue.isGroupQueue()) {
          this.queue.setServerStatus(ServerStatus.OFFLINE);
        }
        resetWaitingWithoutAttempt();
      } else {
        // Backend is reachable. The connection failure was legitimate.
        applyFailedAttempt(config);
      }
    });
  }

  /**
   * Clears the waiting flag and broadcasts the change without counting a failed attempt,
   * leaving the player in the queue for a later retry.
   */
  private void resetWaitingWithoutAttempt() {
    synchronized (this) {
      this.waitingForConnection = false;
    }
    publishWaitingChange();
  }

  /**
   * Clears a stale waiting flag on an entry restored from the depot. A snapshot can capture
   * an entry whose transfer was still pending; without this reset the master would skip it
   * forever via {@code selectCandidate}.
   */
  public void resetWaitingForRetry() {
    synchronized (this) {
      if (!this.waitingForConnection) {
        return;
      }
      this.waitingForConnection = false;
    }
    publishWaitingChange();
  }

  /**
   * Counts a failed connection attempt, refreshes permissions and publishes the waiting
   * change. Enforces the configured retry limit: once reached, the player is notified and
   * removed from the queue.
   *
   * @param config the queue configuration
   */
  protected void applyFailedAttempt(VelocityConfiguration.Queue config) {
    int attempts;
    synchronized (this) {
      this.waitingForConnection = false;
      attempts = ++this.connectionAttempts;
    }
    refreshPermissions();
    publishWaitingChange();

    if (attempts >= config.getMaxSendRetries()) {
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

  public synchronized long getOfflineSinceMs() {
    return offlineSinceMs;
  }

  public synchronized int getOfflineTimeoutSeconds() {
    return offlineTimeoutSeconds;
  }

  /**
   * Records that this player has gone offline with the given timeout. Propagates the
   * change to other proxies via {@link #publishOfflineChange()}.
   */
  public void setOffline(long sinceMs, int timeoutSeconds) {
    synchronized (this) {
      this.offlineSinceMs = sinceMs;
      this.offlineTimeoutSeconds = timeoutSeconds;
    }
    publishOfflineChange();
  }

  /**
   * Clears offline tracking state when a player reconnects within the timeout window.
   * Propagates the change to other proxies via {@link #publishOfflineChange()}.
   */
  public void clearOffline() {
    synchronized (this) {
      this.offlineSinceMs = 0;
      this.offlineTimeoutSeconds = 0;
    }
    publishOfflineChange();
  }

  /**
   * Applies an offline-state update received from another proxy without re-publishing.
   * Both fields are written atomically under the entry's monitor.
   */
  @ApiStatus.Internal
  synchronized void setOfflineFromSync(long sinceMs, int timeoutSeconds) {
    this.offlineSinceMs = sinceMs;
    this.offlineTimeoutSeconds = timeoutSeconds;
  }

  /**
   * Hook for subclasses to broadcast offline-state changes to other proxies.
   * No-op in local (single-proxy) mode.
   */
  protected void publishOfflineChange() {
    // no-op in local mode
  }

  /**
   * Refreshes the player's current permissions into the entry's cached fields.
   */
  protected void refreshPermissions() {
    this.server.getPlayer(this.uniqueId).ifPresent(player -> {
      int newPriority = player.getQueuePriority(this.queue.getName());
      boolean newFullBypass = player.hasPermission("velocity.queue.full.bypass");
      boolean newQueueBypass = player.hasPermission("velocity.queue.bypass");
      synchronized (this) {
        this.priority = newPriority;
        this.fullBypass = newFullBypass;
        this.queueBypass = newQueueBypass;
      }
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
