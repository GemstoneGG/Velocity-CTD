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
import io.netty.buffer.ByteBuf;

/**
 * Serializer for time-based arguments represented as {@link Integer}.
 *
 * <p>This class handles the serialization and deserialization of time-related arguments,
 * converting them to and from an {@link Integer} format.</p>
 */
public class TimeArgumentSerializer implements ArgumentPropertySerializer<Integer> {

  /**
   * A shared singleton instance of {@code TimeArgumentSerializer}.
   */
  static final TimeArgumentSerializer TIME = new TimeArgumentSerializer();

  /**
   * Deserializes a time-based argument from the given {@link ByteBuf}.
   *
   * <p>For protocol versions {@code 1.19.4} and above, this reads a 4-byte {@code int}.
   * For earlier versions, this returns {@code 0}.</p>
   *
   * @param buf the buffer containing the serialized data
   * @param protocolVersion the protocol version to use during deserialization
   * @return the deserialized time value as an {@link Integer}
   */
  @Override
  public Integer deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_4)) {
      return buf.readInt();
    }

    return 0;
  }

  /**
   * Serializes a time-based argument into the given {@link ByteBuf}.
   *
   * <p>For protocol versions {@code 1.19.4} and above, this writes a 4-byte {@code int}.
   * For earlier versions, nothing is written.</p>
   *
   * @param object the time value to serialize
   * @param buf the buffer to write to
   * @param protocolVersion the protocol version to use during serialization
   */
  @Override
  public void serialize(final Integer object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_4)) {
      buf.writeInt(object);
    }
  }
}
