package com.velocitypowered.proxy.queue.pubsub.entities;

import java.util.UUID;

public class QueueAddObj {
    private final String type = "QueueAddObj";
    private final String serverName;
    private final String playerUuid;
    private final int priority;

    public QueueAddObj(String playerUuid, String serverName, int priority) {
        this.playerUuid = playerUuid;
        this.serverName = serverName;
        this.priority = priority;
    }

    public String getServerName() {
        return serverName;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public int getPriority() {
        return priority;
    }
}
