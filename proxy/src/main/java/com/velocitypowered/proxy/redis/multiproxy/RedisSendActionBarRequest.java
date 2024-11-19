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
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Packet that sends an actionbar message to a player.
 *
 * @param playerUuid The UUID of the player.
 * @param componentJson The actionbar message to send.
 */
public record RedisSendActionBarRequest(UUID playerUuid, String componentJson) implements RedisPacket {
  public static final String ID = "redis-send-actionbar-request";
  private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.gson();

  /**
   * Sends an actionbar message to a target. Encodes the given component as JSON text.
   *
   * @param target the target
   * @param component the message to send
   */
  public RedisSendActionBarRequest(UUID target, Component component) {
    this(target, SERIALIZER.serialize(component));
  }


  @Override
  public String getId() {
    return ID;
  }

  /**
   * Gets the component out of this packet, decoded.
   *
   * @return the component in this packet, or {@literal null} if the component was invalid.
   */
  public @Nullable Component component() {
    try {
      return SERIALIZER.deserialize(componentJson);
    } catch (Exception e) {
      return null;
    }
  }
}
