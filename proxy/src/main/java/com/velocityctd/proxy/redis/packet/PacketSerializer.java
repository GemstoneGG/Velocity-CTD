/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.redis.packet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.velocityctd.proxy.redis.packet.serializer.ComponentTypeAdapter;
import java.lang.reflect.Modifier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serializer for {@link DataPacket} objects to and from JSON strings using {@link Gson}.
 */
public final class PacketSerializer {

  /**
   * {@link Gson} instance configured for Redis packet (de)serialization, excluding
   * {@code transient} and {@code static} fields and preserving {@code null} values.
   *
   * <p>Includes a custom type adapter for Adventure {@link Component} objects,
   * allowing them to be used directly as fields in data records.</p>
   */
  private final Gson gson;

  /**
   * Constructs a new {@link PacketSerializer} with default GSON configuration.
   */
  public PacketSerializer() {
    this.gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC)
            .disableHtmlEscaping()
            .serializeNulls()
            .registerTypeHierarchyAdapter(Component.class, new ComponentTypeAdapter())
            .create();
  }

  /**
   * Gets the {@link Gson} instance used by this serializer.
   *
   * @return the configured Gson instance
   */
  public Gson gson() {
    return gson;
  }

  /**
   * Serializes a {@link DataPacket} to a JSON string.
   *
   * @param packet the packet to serialize
   * @return the JSON string representation of the packet
   */
  @NotNull
  public String serialize(@NotNull DataPacket packet) {
    return gson.toJson(packet);
  }

  /**
   * Deserializes a JSON string to a {@link DataPacket}.
   *
   * @param json the JSON string to deserialize
   * @return the deserialized {@link DataPacket}, or {@code null} if deserialization fails
   */
  @Nullable
  public DataPacket deserialize(@NotNull String json) {
    return gson.fromJson(json, DataPacket.class);
  }

  @NotNull
  <T> String serializePayload(T payload) {
    return gson.toJson(payload);
  }

  @Nullable
  <T> T deserializePayload(@NotNull String json, Class<T> payloadClass) {
    return gson.fromJson(json, payloadClass);
  }
}
