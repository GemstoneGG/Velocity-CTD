/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.queue.redis.packet;

import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.ServerStatus;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Data record used to synchronize queue state changes across all proxies.
 *
 * <p>This record is published whenever the queue state changes on any proxy (player enqueued,
 * dequeued, server status changed, queue state changed, or waitingForConnection flag updated).
 * All proxies receive the record and apply the change to their local in-memory queue, ensuring
 * a consistent queue state across the cluster without full-overwrite race conditions.</p>
 */
public record VelocityQueueSync(
    Action action,
    String serverName,
    @Nullable UUID playerUuid,
    @Nullable String username,
    int priority,
    boolean fullBypass,
    boolean queueBypass,
    @Nullable QueueState newState,
    @Nullable ServerStatus newStatus,
    boolean waitingForConnection,
    int connectionAttempts,
    int updatedPriority,
    boolean updatedFullBypass,
    boolean updatedQueueBypass,
    long offlineSinceMs,
    int offlineTimeoutSeconds
) {

  /**
   * The action that triggered this sync.
   */
  public enum Action {
    ENQUEUE,
    DEQUEUE,
    STATE_CHANGE,
    STATUS_CHANGE,
    WAITING_CHANGE,
    OFFLINE_CHANGE
  }

  /**
   * Creates an ENQUEUE sync.
   */
  public static VelocityQueueSync enqueue(String serverName, UUID uuid, String username, int priority,
                                          boolean fullBypass, boolean queueBypass) {
    return new VelocityQueueSync(Action.ENQUEUE, serverName, uuid, username, priority, fullBypass,
        queueBypass, null, null, false, 0, 0, false, false, 0L, 0);
  }

  /**
   * Creates a DEQUEUE sync.
   */
  public static VelocityQueueSync dequeue(String serverName, UUID uuid) {
    return new VelocityQueueSync(Action.DEQUEUE,
        serverName, uuid, null, 0, false, false, null, null, false, 0, 0, false, false, 0L, 0);
  }

  /**
   * Creates a STATE_CHANGE sync.
   */
  public static VelocityQueueSync stateChange(String serverName, QueueState state) {
    return new VelocityQueueSync(Action.STATE_CHANGE, serverName, null, null, 0, false, false,
        state, null, false, 0, 0, false, false, 0L, 0);
  }

  /**
   * Creates a STATUS_CHANGE sync.
   */
  public static VelocityQueueSync statusChange(String serverName, ServerStatus status) {
    return new VelocityQueueSync(Action.STATUS_CHANGE, serverName, null, null, 0, false, false,
        null, status, false, 0, 0, false, false, 0L, 0);
  }

  /**
   * Creates a WAITING_CHANGE sync.
   */
  public static VelocityQueueSync waitingChange(String serverName, UUID uuid, boolean waitingForConnection,
                                                int connectionAttempts, int updatedPriority,
                                                boolean updatedFullBypass, boolean updatedQueueBypass) {
    return new VelocityQueueSync(Action.WAITING_CHANGE, serverName, uuid, null, 0, false, false,
        null, null, waitingForConnection, connectionAttempts, updatedPriority,
        updatedFullBypass, updatedQueueBypass, 0L, 0);
  }

  /**
   * Creates an OFFLINE_CHANGE sync to record when a player goes offline (or clears on reconnect).
   */
  public static VelocityQueueSync offlineChange(String serverName, UUID uuid,
                                                long offlineSinceMs, int offlineTimeoutSeconds) {
    return new VelocityQueueSync(Action.OFFLINE_CHANGE, serverName, uuid, null, 0, false, false,
        null, null, false, 0, 0, false, false, offlineSinceMs, offlineTimeoutSeconds);
  }
}
