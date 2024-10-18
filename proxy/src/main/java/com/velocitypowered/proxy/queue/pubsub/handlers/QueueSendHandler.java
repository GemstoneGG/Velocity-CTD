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
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.pubsub.entities.PubSubHandler;
import com.velocitypowered.proxy.queue.pubsub.entities.QueueSendObj;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * The {@code QueueSendHandler} class is responsible for handling "QueueSendObj" messages received
 * via the Redis Pub/Sub system.
 * <p>
 * When a message is received, this handler processes the JSON payload, retrieves the relevant
 * {@link Player} and {@link RegisteredServer}, and attempts to send the player to the specified server.
 * </p>
 */
public class QueueSendHandler implements PubSubHandler {

  private final VelocityServer velocityServer;

  public QueueSendHandler(VelocityServer velocityServer) {
    this.velocityServer = velocityServer;
  }

  @Override
  public void handle(String jsonString) {
    JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();

    if (!(jsonObject.has("type"))) {
      return;
    }

    if (!(jsonObject.get("type").getAsString().equals("QueueSendObj"))) {
      return;
    }

    QueueSendObj json = velocityServer.getRedisManager().getGson().fromJson(jsonString, new TypeToken<QueueSendObj>() {}.getType());

    Player player = velocityServer.getPlayer(UUID.fromString(json.getUuid())).orElse(null);
    if (player == null) {
      return;
    }

    RegisteredServer server = velocityServer.getServer(json.getServerName()).orElse(null);
    if (server == null) {
      return;
    }

    player.sendMessage(Component.text("Connecting to '" + json.getServerName() + "' now!"));

    player.createConnectionRequest(server).connect().thenAccept(result -> player.sendMessage(Component.text("Connection failed, trying again...")));

  }
}
