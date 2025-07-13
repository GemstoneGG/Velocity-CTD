/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

package com.velocitypowered.proxy.protocol.packet.brigadier;

import com.velocitypowered.api.network.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The {@code ArgumentPropertySerializer} interface defines a contract for serializing and
 * deserializing argument properties to and from a specific format.
 *
 * <p>This interface allows implementations to convert argument properties into a serialized form,
 * which can later be deserialized and restored to their original form. This is particularly useful
 * for persisting command argument configurations or sending them across a network.</p>
 *
 * @param <T> the type of the argument property being serialized
 */
public interface ArgumentPropertySerializer<T> {

  /**
   * Deserializes an argument property from the given buffer using the specified protocol version.
   *
   * @param buf the buffer containing the serialized form
   * @param protocolVersion the protocol version used to interpret the data
   * @return the deserialized argument property, or {@code null} if deserialization fails or is unsupported
   */
  @Nullable T deserialize(ByteBuf buf, ProtocolVersion protocolVersion);

  /**
   * Serializes the given argument property into the buffer using the specified protocol version.
   *
   * @param object the argument property to serialize
   * @param buf the buffer to write to
   * @param protocolVersion the protocol version to use for serialization
   */
  void serialize(T object, ByteBuf buf, ProtocolVersion protocolVersion);
}
