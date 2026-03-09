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

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.velocitypowered.api.network.ProtocolVersion;
import io.netty.buffer.ByteBuf;

/**
 * The {@code IntegerArgumentPropertySerializer} handles serialization and deserialization
 * of {@link IntegerArgumentType}, including optional minimum and maximum constraints.
 *
 * <p>This serializer is used when command arguments require bounded integer values, such
 * as numeric inputs for scores, levels, or configuration parameters.</p>
 *
 * <p>Minimum and maximum bounds are encoded using a flag byte, followed by their values
 * only if the respective flags are set.</p>
 */
final class IntegerArgumentPropertySerializer implements ArgumentPropertySerializer<IntegerArgumentType> {

  /**
   * A shared singleton instance of {@code IntegerArgumentPropertySerializer}.
   */
  static final IntegerArgumentPropertySerializer INTEGER = new IntegerArgumentPropertySerializer();

  /**
   * Flag bit indicating that a minimum value is present in the serialized data.
   */
  static final byte HAS_MINIMUM = 0x01;

  /**
   * Flag bit indicating that a maximum value is present in the serialized data.
   */
  static final byte HAS_MAXIMUM = 0x02;

  private IntegerArgumentPropertySerializer() {
  }

  @Override
  public IntegerArgumentType deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    byte flags = buf.readByte();
    int minimum = (flags & HAS_MINIMUM) != 0 ? buf.readInt() : Integer.MIN_VALUE;
    int maximum = (flags & HAS_MAXIMUM) != 0 ? buf.readInt() : Integer.MAX_VALUE;
    return IntegerArgumentType.integer(minimum, maximum);
  }

  @Override
  public void serialize(final IntegerArgumentType object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
    boolean hasMinimum = object.getMinimum() != Integer.MIN_VALUE;
    boolean hasMaximum = object.getMaximum() != Integer.MAX_VALUE;
    byte flag = getFlags(hasMinimum, hasMaximum);

    buf.writeByte(flag);
    if (hasMinimum) {
      buf.writeInt(object.getMinimum());
    }

    if (hasMaximum) {
      buf.writeInt(object.getMaximum());
    }
  }

  static byte getFlags(final boolean hasMinimum, final boolean hasMaximum) {
    byte flags = 0;
    if (hasMinimum) {
      flags |= HAS_MINIMUM;
    }

    if (hasMaximum) {
      flags |= HAS_MAXIMUM;
    }

    return flags;
  }
}
