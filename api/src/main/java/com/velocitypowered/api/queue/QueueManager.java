package com.velocitypowered.api.queue;

import com.velocitypowered.api.proxy.Player;

import java.util.UUID;

public interface QueueManager {
    void add(String serverName, UUID playerUuid, int priority);
    void remove(UUID uuid);
}
