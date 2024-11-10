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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a message to a target.
 *
 * @param target the target
 * @param componentJson the message to send, encoded as JSON
 */
public record RedisSendMessage(EncodedCommandSource target, String componentJson) implements RedisPacket {
  private static final Logger logger = LoggerFactory.getLogger(RedisSendMessage.class);
  private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.gson();
  public static final String ID = "send-message";

  /**
   * Sends a message to a target. Encodes the given component as JSON text.
   *
   * @param target the target
   * @param component the message to send
   */
  public RedisSendMessage(EncodedCommandSource target, Component component) {
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
      logger.warn("invalid component sent in `RedisSendMessage` packet", e);
      return null;
    }
  }
}
