package com.velocitypowered.proxy.queue.pubsub.entities;

import java.util.UUID;

public class QueueSendMessageObj {
    private final String type = "QueueSendMessageObj";
    private final String uuid;
    private final String message;

    public QueueSendMessageObj(String uuid, String message) {
        this.uuid = uuid;
        this.message = message;
    }

    public String getUuid() {
        return uuid;
    }

    public String getMessage() {
        return message;
    }
}
