package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.redis.RedisPacket;
import org.checkerframework.checker.nullness.qual.Nullable;

public record ShuttingDown(String proxyId) implements RedisPacket {
  public static final String ID = "shutting-down";

  @Override
  public String getId() {
    return ID;
  }
}
