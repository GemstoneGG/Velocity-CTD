/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.velocityctd.proxy.redis.packet.serializer.ComponentDeserializer;
import com.velocityctd.proxy.redis.packet.serializer.ComponentSerializer;
import java.io.IOException;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * Serializer for {@link DataPacket} objects to and from binary data using Jackson and MessagePack.
 */
public final class PacketSerializer {

  /**
   * {@link ObjectMapper} instance configured for Redis packet (de)serialization, using MessagePack.
   */
  private final ObjectMapper mapper;

  /**
   * Constructs a new {@link PacketSerializer} with MessagePack configuration.
   */
  public PacketSerializer() {
    this.mapper = new ObjectMapper(new MessagePackFactory());
    this.mapper.setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
    this.mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    this.mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    SimpleModule module = new SimpleModule();
    module.addSerializer(Component.class, new ComponentSerializer());
    module.addDeserializer(Component.class, new ComponentDeserializer());
    this.mapper.registerModule(module);
  }

  /**
   * Gets the {@link ObjectMapper} instance used by this serializer.
   *
   * @return the configured ObjectMapper instance
   */
  public ObjectMapper mapper() {
    return mapper;
  }

  /**
   * Serializes a {@link DataPacket} to binary data.
   *
   * @param packet the packet to serialize
   * @return the binary representation of the packet
   */
  public byte @NotNull [] serialize(@NotNull DataPacket packet) {
    try {
      return mapper.writeValueAsBytes(packet);
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize packet", e);
    }
  }

  /**
   * Deserializes binary data to a {@link DataPacket}.
   *
   * @param data the binary data to deserialize
   * @return the deserialized {@link DataPacket}, or {@code null} if deserialization fails
   */
  @Nullable
  public DataPacket deserialize(byte @NotNull [] data) {
    try {
      return mapper.readValue(data, DataPacket.class);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Serializes a payload object to binary data.
   *
   * @param payload the object to serialize
   * @param <T> the type of the payload
   * @return the binary representation of the payload
   */
  <T> byte @NotNull [] serializePayload(T payload) {
    try {
      return mapper.writeValueAsBytes(payload);
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize payload", e);
    }
  }

  /**
   * Deserializes binary data to a specific payload class.
   *
   * @param data the binary data to deserialize
   * @param payloadClass the class to deserialize into
   * @param <T> the type of the payload
   * @return the deserialized payload object, or {@code null} if deserialization fails
   */
  @Nullable
  <T> T deserializePayload(byte @NotNull [] data, Class<T> payloadClass) {
    try {
      return mapper.readValue(data, payloadClass);
    } catch (IOException e) {
      throw new RuntimeException("Failed to deserialize payload", e);
    }
  }
}
