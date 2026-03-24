/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

package com.velocityctd.proxy.redis.packet.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.velocityctd.proxy.redis.packet.GenericPacket;
import com.velocityctd.proxy.redis.packet.RedisPacket;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a utility class for serializing {@link RedisPacket} objects to JSON strings using {@link Gson}.
 */
public final class PacketSerializer {

  /**
   * Logger used for security warnings when blocked packet types are encountered.
   */
  private static final Logger LOGGER = LogManager.getLogger(PacketSerializer.class);

  /**
   * Shared {@link Gson} instance configured for Redis packet (de)serialization, excluding
   * {@code transient} and {@code static} fields and preserving {@code null} values.
   */
  public static final Gson GSON = new GsonBuilder()
          .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC)
          .disableHtmlEscaping()
          .serializeNulls()
          .create();

  /**
   * Whitelist of allowed Redis packet classes that can be deserialized.
   * Only classes in this set can be loaded via Class.forName() to prevent
   * Remote Code Execution (RCE) attacks via malicious JSON payloads.
   */
  private static final Set<String> ALLOWED_PACKET_TYPES = Set.of(
      // Base packet types
      "com.velocityctd.proxy.redis.packet.GenericPacket",
      "com.velocityctd.proxy.redis.packet.EmptyPacket",

      // Typed packets
      "com.velocityctd.proxy.redis.packet.typed.ComponentPacket",
      "com.velocityctd.proxy.redis.packet.typed.StringPacket",
      "com.velocityctd.proxy.redis.packet.typed.BooleanPacket",
      "com.velocityctd.proxy.redis.packet.typed.IntegerPacket",
      "com.velocityctd.proxy.redis.packet.typed.LongPacket",
      "com.velocityctd.proxy.redis.packet.typed.UuidPacket",
      "com.velocityctd.proxy.redis.packet.typed.MapPacket",
      "com.velocityctd.proxy.redis.packet.typed.RecordPacket",

      // Concrete packets
      "com.velocityctd.proxy.redis.impl.packet.VelocityActionBar",
      "com.velocityctd.proxy.redis.impl.packet.VelocityAlert",
      "com.velocityctd.proxy.redis.impl.packet.VelocityKick",
      "com.velocityctd.proxy.redis.impl.packet.VelocityMessage",
      "com.velocityctd.proxy.redis.impl.packet.VelocityRemote",
      "com.velocityctd.proxy.redis.impl.packet.VelocitySudo",
      "com.velocityctd.proxy.redis.impl.packet.VelocitySwitchServer",
      "com.velocityctd.proxy.queue.redis.packet.VelocityQueueTransfer",
      "com.velocityctd.proxy.queue.redis.packet.VelocityQueueSync",

      // Transaction types
      "com.velocityctd.proxy.redis.impl.transaction.VelocityGetPlayerPing",
      "com.velocityctd.proxy.redis.impl.transaction.VelocityReload",
      "com.velocityctd.proxy.redis.impl.transaction.VelocityTransferRemote",
      "com.velocityctd.proxy.redis.impl.transaction.VelocityUptime"
  );

  /**
   * Validates that a class name is in the allowed whitelist.
   *
   * @param className the fully qualified class name to validate
   * @return true if the class is allowed for deserialization, false otherwise
   */
  private static boolean isAllowedPacketType(final @NotNull String className) {
    return ALLOWED_PACKET_TYPES.contains(className);
  }

  /**
   * Serializes a {@link RedisPacket} to a JSON string using {@link Gson}.
   *
   * @param packet the packet to serialize as a JSON string
   * @param <T>    the type of the packet
   * @return the JSON string representation of the packet
   */
  @NotNull
  public static <T extends RedisPacket> String serialize(final @NotNull T packet) {
    return PacketSerializer.GSON.toJson(packet);
  }

  /**
   * Deserializes a JSON string to a {@link RedisPacket} object using {@link Gson}.
   *
   * @param serializedPacket the JSON string to deserialize
   * @param packetClass      the concrete packet class to deserialize into
   * @param <T>              the class of the packet
   * @return the deserialized {@link RedisPacket} object, or {@code null} if the deserialization fails
   */
  @Nullable
  public static <T extends RedisPacket> T deserialize(final @NotNull String serializedPacket, final Class<T> packetClass) {
    return PacketSerializer.GSON.fromJson(serializedPacket, packetClass);
  }

  /**
   * Deserializes a JSON string to a {@link RedisPacket} object using {@link Gson}.
   * The type field in the JSON is validated against the whitelist before deserialization
   * to prevent Remote Code Execution (RCE) attacks.
   *
   * @param serializedPacket the JSON string to deserialize
   * @param <T>              the type of the packet
   * @return the deserialized {@link RedisPacket} object, or null if the deserialization fails
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public static <T extends RedisPacket> T deserialize(final @NotNull String serializedPacket) {
    final RedisPacket redisPacket = PacketSerializer.GSON.fromJson(serializedPacket, GenericPacket.class);
    if (redisPacket == null) {
      return null;
    }

    final String typeName = redisPacket.getType();
    if (typeName == null || typeName.isBlank()) {
      return (T) redisPacket;
    }

    // Validate the type is in the allowed whitelist
    if (!isAllowedPacketType(typeName)) {
      LOGGER.warn("Blocked deserialization of packet type '{}' (not in whitelist). "
          + "Possible malicious Redis packet injection.", typeName);
      return (T) redisPacket;
    }

    try {
      final Class<T> type = (Class<T>) Class.forName(typeName);
      return PacketSerializer.GSON.fromJson(serializedPacket, type);
    } catch (ClassNotFoundException ignored) {
      // Class not found, return the generic packet
      return (T) redisPacket;
    }
  }

  /**
   * Prepares a JSON string for serialization by checking if the serialized packet contains a type field.
   *
   * @param serializedPacket the JSON string to prepare
   * @return the type field of the serialized packet, or null if the type field is not present
   */
  @Nullable
  public static String prepare(final @NotNull String serializedPacket) {
    final JsonObject jsonObject = PacketSerializer.GSON.fromJson(serializedPacket, JsonObject.class);
    if (jsonObject == null || !jsonObject.has("type")) {
      return null;
    }

    return jsonObject.getAsJsonPrimitive("type").getAsString();
  }
}
