package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.redis.RedisPacket;

/**
 * Announcement of a proxy ID.
 * @param proxyId the ID to announce.
 * @param wantsReply whether this proxy is soliciting a reply.
 */
public record ProxyIDAnnouncement(String proxyId, boolean wantsReply) implements RedisPacket {
  public static final String ID = "id-announcement";

  @Override
  public String getId() {
    return ID;
  }
}

