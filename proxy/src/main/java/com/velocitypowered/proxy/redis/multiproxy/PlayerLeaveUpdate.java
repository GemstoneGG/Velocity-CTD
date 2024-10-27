package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.redis.RedisPacket;

import java.util.UUID;

public record PlayerLeaveUpdate(String proxyId, UUID uuid) implements RedisPacket {
  public static final String ID = "player-leave";

  @Override
  public String getId() {
    return ID;
  }
}