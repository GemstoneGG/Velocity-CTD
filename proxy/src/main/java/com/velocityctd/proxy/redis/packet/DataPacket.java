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

package com.velocityctd.proxy.redis.packet;

import com.velocityctd.proxy.redis.packet.serialization.PacketSerializer;
import org.jetbrains.annotations.NotNull;

/**
 * A generic reply packet that carries any GSON-serializable payload.
 *
 * <p>The payload is stored as a pre-serialized JSON string alongside its
 * fully qualified class name, allowing type-safe deserialization without
 * needing a concrete packet subclass per data type.</p>
 */
public final class DataPacket extends AbstractRedisPacket {

  /**
   * The GSON-serialized JSON representation of the payload.
   */
  private final String data;

  /**
   * The fully qualified class name of the payload type,
   * used for deserialization.
   */
  private final String dataType;

  /**
   * Constructs a new {@link DataPacket} by serializing the given payload.
   *
   * @param payload the payload to serialize
   * @param <T> the type of the payload
   */
  public <T> DataPacket(final @NotNull T payload) {
    this.data = PacketSerializer.GSON.toJson(payload);
    this.dataType = payload.getClass().getName();
  }

  /**
   * Deserializes the payload into the specified type.
   *
   * @param <T> the target type
   * @return the deserialized payload
   */
  public <T> T getData() {
    Class<?> clazz;
    try {
      clazz = Class.forName(dataType);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    //noinspection unchecked
    return (T) PacketSerializer.GSON.fromJson(data, clazz);
  }
}
