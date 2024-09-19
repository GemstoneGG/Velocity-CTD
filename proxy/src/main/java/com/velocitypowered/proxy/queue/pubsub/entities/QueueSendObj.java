package com.velocitypowered.proxy.queue.pubsub.entities;

import java.util.UUID;

public class QueueSendObj {
    private final String type = "QueueSendObj";
    private final String uuid;
    private final String serverName;

    public QueueSendObj(String uuid, String serverName) {
        this.uuid = uuid;
        this.serverName = serverName;
    }

    public String getUuid() {
        return uuid;
    }

    public String getServerName() {
        return serverName;
    }
}
