package com.velocitypowered.proxy.queue.pubsub;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.pubsub.handlers.QueueAddHandler;
import com.velocitypowered.proxy.queue.pubsub.handlers.QueueSendHandler;
import com.velocitypowered.proxy.queue.pubsub.handlers.QueueSendMessageHandler;
import redis.clients.jedis.JedisPubSub;

public class MainPubSub extends JedisPubSub {
    private final VelocityServer velocityServer;

    private final QueueSendHandler queueSendHandler;
    private final QueueSendMessageHandler queueSendMessageHandler;
    private final QueueAddHandler queueAddHandler;

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
