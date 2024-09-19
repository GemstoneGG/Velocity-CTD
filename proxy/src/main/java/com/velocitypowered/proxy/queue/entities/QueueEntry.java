package com.velocitypowered.proxy.queue.entities;

import java.util.UUID;

public class QueueEntry {
    private final UUID uuid;
    private final long priority;

    public QueueEntry(UUID uuid, long priority) {
        this.uuid = uuid;
        this.priority = priority;
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getPriority() {
        return priority;
    }
}
