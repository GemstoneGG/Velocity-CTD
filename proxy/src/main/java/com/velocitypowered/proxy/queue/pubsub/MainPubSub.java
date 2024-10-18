/*
 * Copyright (C) 2018-2024 Velocity Contributors
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

package com.velocitypowered.proxy.queue.pubsub;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.pubsub.handlers.QueueAddHandler;
import com.velocitypowered.proxy.queue.pubsub.handlers.QueueSendHandler;
import com.velocitypowered.proxy.queue.pubsub.handlers.QueueSendMessageHandler;
import redis.clients.jedis.JedisPubSub;

/**
 * The {@code MainPubSub} class is responsible for managing Redis Pub/Sub messaging
 * related to the queue system in the Velocity proxy.
 * <p>
 * This class extends {@link JedisPubSub} and listens to Redis channels to handle
 * incoming messages. When a message is received, it is processed by different handlers,
 * each responsible for specific queue-related actions, such as sending players to a server,
 * adding players to the queue, or sending messages.
 * </p>
 */
public class MainPubSub extends JedisPubSub {

  private final VelocityServer velocityServer;

  private final QueueSendHandler queueSendHandler;
  private final QueueSendMessageHandler queueSendMessageHandler;
  private final QueueAddHandler queueAddHandler;

  /**
   * Constructs a new {@code MainPubSub} instance with the specified {@link VelocityServer}.
   *
   * @param velocityServer the {@link VelocityServer} instance to be used for handling queue events.
   */
  public MainPubSub(VelocityServer velocityServer) {
    this.velocityServer = velocityServer;
    queueSendHandler = new QueueSendHandler(velocityServer);
    queueSendMessageHandler = new QueueSendMessageHandler(velocityServer);
    queueAddHandler = new QueueAddHandler(velocityServer);
  }

  @Override
  public void onMessage(String channel, String message) {
    queueSendHandler.handle(message);
    queueSendMessageHandler.handle(message);
    queueAddHandler.handle(message);
  }
}
