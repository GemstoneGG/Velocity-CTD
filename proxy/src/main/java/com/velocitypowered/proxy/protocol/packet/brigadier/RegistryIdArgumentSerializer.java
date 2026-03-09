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
 * The {@code RegistryIdArgumentSerializer} handles serialization and deserialization
 * of integer-based registry ID arguments.
 *
 * <p>This serializer is used for command arguments that refer to elements in Minecraft
 * registries (e.g., items, entities, dimensions) by their numerical registry ID.</p>
 *
 * <p>Values are encoded as variable-length integers using {@link ProtocolUtils}
 * for compact transmission.</p>
 */
public class RegistryIdArgumentSerializer implements ArgumentPropertySerializer<Integer> {

  /**
   * A shared singleton instance of the {@code RegistryIdArgumentSerializer}.
   */
  static final RegistryIdArgumentSerializer REGISTRY_ID = new RegistryIdArgumentSerializer();

  /**
   * Deserializes an integer registry ID from the given {@link ByteBuf}.
   *
   * <p>This method uses VarInt encoding as defined in the Minecraft protocol.</p>
   *
   * @param buf the input buffer containing the encoded integer
   * @param protocolVersion the protocol version in use
   * @return the decoded registry ID as an {@link Integer}
   */
  @Override
  public Integer deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    return ProtocolUtils.readVarInt(buf);
  }

  /**
   * Serializes the given registry ID into the specified {@link ByteBuf}.
   *
   * <p>The integer is encoded as a VarInt for efficient transmission.</p>
   *
   * @param object the registry ID to encode
   * @param buf the output buffer to write the data into
   * @param protocolVersion the protocol version in use
   */
  @Override
  public void serialize(final Integer object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, object);
  }
}
