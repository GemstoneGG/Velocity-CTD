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
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.velocityctd.proxy.redis.packet.DataPacket;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for serializing and deserializing {@link DataPacket} objects
 * to and from JSON strings using {@link Gson}.
 */
public final class PacketSerializer {

  /**
   * Shared {@link Gson} instance configured for Redis packet (de)serialization, excluding
   * {@code transient} and {@code static} fields and preserving {@code null} values.
   *
   * <p>Includes a custom type adapter for Adventure {@link Component} objects,
   * allowing them to be used directly as fields in data records.</p>
   */
  public static final Gson GSON = new GsonBuilder()
          .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC)
          .disableHtmlEscaping()
          .serializeNulls()
          .registerTypeHierarchyAdapter(Component.class, new ComponentTypeAdapter())
          .create();

  /**
   * Serializes a {@link DataPacket} to a JSON string.
   *
   * @param packet the packet to serialize
   * @return the JSON string representation of the packet
   */
  @NotNull
  public static String serialize(final @NotNull DataPacket packet) {
    return GSON.toJson(packet);
  }

  /**
   * Deserializes a JSON string to a {@link DataPacket}.
   *
   * @param json the JSON string to deserialize
   * @return the deserialized {@link DataPacket}, or {@code null} if deserialization fails
   */
  @Nullable
  public static DataPacket deserialize(final @NotNull String json) {
    return GSON.fromJson(json, DataPacket.class);
  }

  /**
   * GSON type adapter that bridges Adventure's {@link Component} type with
   * the {@link GsonComponentSerializer}, allowing components to be used as
   * direct fields in data records without manual serialization.
   */
  private static final class ComponentTypeAdapter
          implements JsonSerializer<Component>, JsonDeserializer<Component> {

    private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.gson();

    @Override
    public JsonElement serialize(final Component src, final Type typeOfSrc,
                                 final JsonSerializationContext context) {
      return SERIALIZER.serializeToTree(src);
    }

    @Override
    public Component deserialize(final JsonElement json, final Type typeOfT,
                                 final JsonDeserializationContext context) throws JsonParseException {
      return SERIALIZER.deserializeFromTree(json);
    }
  }
}
