package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.redis.RedisPacket;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.UUID;

public record PlayerServerChange(String proxyId, UUID uuid, @Nullable String server) implements RedisPacket {
  public static final String ID = "player-server-change";

  @Override
  public String getId() {
    return ID;
  }
}