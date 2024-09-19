package com.velocitypowered.proxy.queue.pubsub.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.pubsub.entities.PubSubHandler;
import com.velocitypowered.proxy.queue.pubsub.entities.QueueSendMessageObj;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class QueueSendMessageHandler implements PubSubHandler {

    private final VelocityServer velocityServer;

    public QueueSendMessageHandler(VelocityServer velocityServer) {
        this.velocityServer = velocityServer;
    }

    @Override
    public void handle(String jsonString) {
        JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
        if(!(jsonObject.has("type"))) return;
        if(!(jsonObject.get("type").getAsString().equals("QueueSendMessageObj"))) return;

        QueueSendMessageObj json = velocityServer.getRedisManager().getGson().fromJson(jsonString, new TypeToken<QueueSendMessageObj>() {}.getType());


        Player player = velocityServer.getPlayer(UUID.fromString(json.getUuid())).orElse(null);
        if(player == null) return;

        player.sendMessage(Component.text(json.getMessage()));
    }
}
