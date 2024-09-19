package com.velocitypowered.proxy.queue.pubsub.entities;

public interface PubSubHandler {
    void handle(String jsonString);
}
