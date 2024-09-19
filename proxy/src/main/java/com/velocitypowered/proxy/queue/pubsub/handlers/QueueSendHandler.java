package com.velocitypowered.proxy.queue.pubsub.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.pubsub.entities.PubSubHandler;
import com.velocitypowered.proxy.queue.pubsub.entities.QueueSendObj;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class QueueSendHandler implements PubSubHandler {

    private final VelocityServer velocityServer;

    public QueueSendHandler(VelocityServer velocityServer) {
        this.velocityServer = velocityServer;
    }

    @Override
    public void handle(String jsonString) {
        JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
        if(!(jsonObject.has("type"))) return;
        if(!(jsonObject.get("type").getAsString().equals("QueueSendObj"))) return;

        QueueSendObj json = velocityServer.getRedisManager().getGson().fromJson(jsonString, new TypeToken<QueueSendObj>() {}.getType());


        Player player = velocityServer.getPlayer(UUID.fromString(json.getUuid())).orElse(null);
        if(player == null) return;

        RegisteredServer server = velocityServer.getServer(json.getServerName()).orElse(null);
        if(server == null) return;


        player.sendMessage(Component.text("Connecting to '" + json.getServerName() + "' now!"));
        player.createConnectionRequest(server).connect().thenAccept(result -> {
            player.sendMessage(Component.text("Connection failed, trying again..."));
        });

    }
}
