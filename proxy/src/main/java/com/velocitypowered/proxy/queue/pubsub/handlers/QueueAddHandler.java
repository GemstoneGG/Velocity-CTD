package com.velocitypowered.proxy.queue.pubsub.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.pubsub.entities.PubSubHandler;
import com.velocitypowered.proxy.queue.pubsub.entities.QueueAddObj;
import com.velocitypowered.proxy.queue.pubsub.entities.QueueSendObj;

import java.util.UUID;

public class QueueAddHandler implements PubSubHandler {

    private final VelocityServer velocityServer;

    public QueueAddHandler(VelocityServer velocityServer) {
        this.velocityServer = velocityServer;
    }

    @Override
    public void handle(String jsonString) {
        JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
        if(!(jsonObject.has("type"))) return;
        if(!(jsonObject.get("type").getAsString().equals("QueueAddObj"))) return;

        QueueAddObj json = velocityServer.getRedisManager().getGson().fromJson(jsonString, new TypeToken<QueueAddObj>() {}.getType());

        velocityServer.getQueueManager().add(json.getServerName(), UUID.fromString(json.getPlayerUuid()), json.getPriority());


    }
}
