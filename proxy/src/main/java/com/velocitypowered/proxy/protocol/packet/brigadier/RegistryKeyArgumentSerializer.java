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

package com.velocitypowered.proxy.protocol.packet.brigadier;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * Serializer for {@link RegistryKeyArgument} objects.
 *
 * <p>This class handles the serialization and deserialization of {@code RegistryKeyArgument}
 * objects to and from a {@link ByteBuf} using the specified {@link ProtocolVersion}.</p>
 */
public class RegistryKeyArgumentSerializer implements ArgumentPropertySerializer<RegistryKeyArgument> {

  /**
   * A shared singleton instance of {@code RegistryKeyArgumentSerializer}.
   */
  static final RegistryKeyArgumentSerializer REGISTRY = new RegistryKeyArgumentSerializer();

  /**
   * Deserializes a {@link RegistryKeyArgument} from the given {@link ByteBuf}.
   *
   * @param buf the buffer containing the serialized registry key argument
   * @param protocolVersion the protocol version to use during deserialization
   * @return the deserialized {@link RegistryKeyArgument} instance
   */
  @Override
  public RegistryKeyArgument deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    return new RegistryKeyArgument(ProtocolUtils.readString(buf));
  }

  /**
   * Serializes the given {@link RegistryKeyArgument} to the specified {@link ByteBuf}.
   *
   * @param object the registry key argument to serialize
   * @param buf the buffer to write the serialized data into
   * @param protocolVersion the protocol version to use during serialization
   */
  @Override
  public void serialize(final RegistryKeyArgument object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeString(buf, object.getIdentifier());
  }
}
