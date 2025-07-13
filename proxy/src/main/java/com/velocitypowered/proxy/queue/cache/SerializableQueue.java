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

package com.velocitypowered.proxy.queue.cache;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.ServerQueueEntry;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.queue.ServerStatus;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Represents a serializable queue.
 */
public class SerializableQueue {

  /**
   * The serialized queue entries representing players currently in the queue.
   */
  private final Deque<SerializableQueueEntry> queue = new ConcurrentLinkedDeque<>();

  /**
   * The name of the server this queue belongs to.
   */
  private final String serverName;

  /**
   * The online status of the server when the queue was serialized.
   */
  private final ServerStatus online;

  /**
   * Indicates whether the queue is full at the time of serialization.
   */
  private final boolean full;

  /**
   * Indicates whether the queue is currently paused.
   */
  private final boolean paused;

  /**
   * Constructs a new serializable queue.
   *
   * @param status The real queue.
   */
  public SerializableQueue(final ServerQueueStatus status) {
    status.getQueue().forEach(e -> queue.add(new SerializableQueueEntry(e.getPlayer(),
        e.getConnectionAttempts(),
        e.isWaitingForConnection(),
        e.getPriority(),
        e.isFullBypass(),
        e.isQueueBypass())));

    online = status.getStatus();
    this.full = status.isFull();
    this.paused = status.isPaused();
    this.serverName = status.getServerName();
  }

  /**
   * Converts the JSON to real object.
   *
   * @param proxy The proxy.
   * @param server The server.
   * @return The real object.
   */
  public ServerQueueStatus convert(final VelocityServer proxy,
                                   final VelocityRegisteredServer server) {
    final Deque<ServerQueueEntry> entries = new ConcurrentLinkedDeque<>();
    queue.forEach(q -> entries.add(new ServerQueueEntry(q.uuid(),
        server,
        proxy,
        q.connectionAttempts(),
        q.waitingForConnection(),
        q.priority(),
        q.fullBypass(),
        q.queueBypass())));
    return new ServerQueueStatus(server, proxy, entries, online, full, paused);
  }

  /**
   * Gets the queue.
   *
   * @return the queue.
   */
  public Deque<SerializableQueueEntry> getQueue() {
    return queue;
  }

  /**
   * Gets the status of the queue.
   *
   * @return The status.
   */
  public ServerStatus getOnline() {
    return online;
  }

  /**
   * Checks if the queue is full.
   *
   * @return The full status.
   */
  public boolean isFull() {
    return full;
  }

  /**
   * Gets the paused status.
   *
   * @return The paused status.
   */
  public boolean isPaused() {
    return paused;
  }

  /**
   * Gets the server name.
   *
   * @return The server name.
   */
  public String getServerName() {
    return serverName;
  }
}
