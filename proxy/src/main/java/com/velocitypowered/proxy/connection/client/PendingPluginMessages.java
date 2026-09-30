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

package com.velocitypowered.proxy.connection.client;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Caps the plugin messages a connection may have waiting on a {@link PluginMessageEvent}. Each
 * holds a copy of its payload until the event's handlers finish, so without these caps a client
 * flooding a channel that a plugin listens on grows the heap for as long as those handlers lag
 * behind. Every successful reservation is released once its event is done.
 */
final class PendingPluginMessages {

  private static final Logger LOGGER = LogManager.getLogger(PendingPluginMessages.class);

  private static final long MAX_PENDING_PLUGIN_MESSAGE_BYTES =
      Long.getLong("velocity.max-pending-plugin-message-bytes", 4L * 1024 * 1024);
  private static final int MAX_PENDING_PLUGIN_MESSAGES =
      Integer.getInteger("velocity.max-pending-plugin-messages", 1024);

  private final ConnectedPlayer player;
  private final AtomicLong bytes = new AtomicLong();
  private final AtomicInteger count = new AtomicInteger();
  private volatile boolean overflowed;

  PendingPluginMessages(ConnectedPlayer player) {
    this.player = player;
  }

  /**
   * Reserves room for a plugin message waiting on its {@link PluginMessageEvent}, enforcing the
   * per-connection byte and count caps.
   *
   * @param size the payload size of the message
   * @return {@code false} (after disconnecting the player) when the message would exceed the caps
   */
  boolean reserve(int size) {
    if (overflowed) {
      return false;
    }
    long newBytes = bytes.addAndGet(size);
    int newCount = count.incrementAndGet();
    if (newBytes > MAX_PENDING_PLUGIN_MESSAGE_BYTES || newCount > MAX_PENDING_PLUGIN_MESSAGES) {
      overflowed = true;
      release(size);
      LOGGER.warn("Disconnecting {}: plugin messages waiting on events exceeded their limits "
          + "({} messages, {} bytes).", player, newCount, newBytes);
      player.disconnect(Component.translatable("velocity.error.pending-plugin-message-overflow"));
      return false;
    }
    return true;
  }

  /**
   * Releases the room a message reserved once its event is done.
   *
   * @param size the payload size the message reserved
   */
  void release(int size) {
    bytes.addAndGet(-size);
    count.decrementAndGet();
  }
}
