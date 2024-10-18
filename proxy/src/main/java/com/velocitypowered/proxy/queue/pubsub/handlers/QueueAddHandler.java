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

package com.velocitypowered.proxy.queue.pubsub.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.pubsub.entities.PubSubHandler;
import com.velocitypowered.proxy.queue.pubsub.entities.QueueAddObj;
import java.util.UUID;

/**
 * The {@code QueueAddHandler} class is responsible for handling "QueueAddObj" messages received
 * via the Redis Pub/Sub system.
 * <p>
 * When a message is received, this handler processes the JSON payload, parses it into a
 * {@link QueueAddObj}, and adds the player to the queue for the specified server using
 * the {@link com.velocitypowered.api.queue.QueueManager}.
 * </p>
 */
public class QueueAddHandler implements PubSubHandler {

  private final VelocityServer velocityServer;

  public QueueAddHandler(VelocityServer velocityServer) {
    this.velocityServer = velocityServer;
  }

  @Override
  public void handle(String jsonString) {
    JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();

    if (!(jsonObject.has("type"))) {
      return;
    }

    if (!(jsonObject.get("type").getAsString().equals("QueueAddObj"))) {
      return;
    }

    QueueAddObj json = velocityServer.getRedisManager().getGson().fromJson(jsonString, new TypeToken<QueueAddObj>() {}.getType());

    velocityServer.getQueueManager().add(json.getServerName(), UUID.fromString(json.getPlayerUuid()), json.getPriority());

  }
}
