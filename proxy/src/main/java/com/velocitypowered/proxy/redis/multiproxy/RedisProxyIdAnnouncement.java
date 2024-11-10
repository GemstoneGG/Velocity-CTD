/*
 * Copyright (C) 2024 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.redis.RedisPacket;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Announcement of a proxy ID.
 *
 * @param proxyId the ID to announce.
 * @param wantsReply whether this proxy is soliciting a reply.
 * @param players the players on this proxy
 */
public record RedisProxyIdAnnouncement(String proxyId, boolean wantsReply,
                                       @Nullable List<MultiProxyHandler.RemotePlayerInfo> players) implements RedisPacket {
  public static final String ID = "id-announcement";

  @Override
  public String getId() {
    return ID;
  }
}
