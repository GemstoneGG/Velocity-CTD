package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.redis.RedisPacket;

import java.util.UUID;

public record PlayerJoinUpdate(String proxyId, UUID uuid, String name) implements RedisPacket {
  public static final String ID = "player-join";

  @Override
  public String getId() {
    return ID;
  }
}
