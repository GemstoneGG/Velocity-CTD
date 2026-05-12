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

public final class PacketSerializer {

  private final ObjectMapper mapper;

  public PacketSerializer() {
    this.mapper = new ObjectMapper(new MessagePackFactory());
    this.mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    this.mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    this.mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    SimpleModule module = new SimpleModule();
    module.addSerializer(Component.class, new ComponentSerializer());
    module.addDeserializer(Component.class, new ComponentDeserializer());
    this.mapper.registerModule(module);
  }

  public ObjectMapper mapper() {
    return mapper;
  }

  @NotNull
  public byte[] serialize(@NotNull DataPacket packet) {
    try {
      return mapper.writeValueAsBytes(packet);
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize packet", e);
    }
  }

  @Nullable
  public DataPacket deserialize(@NotNull byte[] data) {
    try {
      return mapper.readValue(data, DataPacket.class);
    } catch (IOException e) {
      return null;
    }
  }

  @NotNull
  <T> byte[] serializePayload(T payload) {
    try {
      return mapper.writeValueAsBytes(payload);
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize payload", e);
    }
  }

  @Nullable
  <T> T deserializePayload(@NotNull byte[] data, Class<T> payloadClass) {
    try {
      return mapper.readValue(data, payloadClass);
    } catch (IOException e) {
      throw new RuntimeException("Failed to deserialize payload", e);
    }
  }
}
