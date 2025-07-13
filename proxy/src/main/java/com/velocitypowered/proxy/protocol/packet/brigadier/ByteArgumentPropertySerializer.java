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

/**
 * The {@code ByteArgumentPropertySerializer} is a concrete implementation of
 * {@link ArgumentPropertySerializer} that handles serialization and deserialization
 * of {@link Byte} values.
 *
 * <p>This serializer is used for argument types where a single byte is sufficient to
 * represent the argument's metadata or configuration, such as certain Minecraft selectors
 * or flags.</p>
 */
final class ByteArgumentPropertySerializer implements ArgumentPropertySerializer<Byte> {

  /**
   * A shared singleton instance of {@code ByteArgumentPropertySerializer}.
   */
  static final ByteArgumentPropertySerializer BYTE = new ByteArgumentPropertySerializer();

  private ByteArgumentPropertySerializer() {
  }

  @Override
  public Byte deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    return buf.readByte();
  }

  @Override
  public void serialize(final Byte object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
    buf.writeByte(object);
  }
}
