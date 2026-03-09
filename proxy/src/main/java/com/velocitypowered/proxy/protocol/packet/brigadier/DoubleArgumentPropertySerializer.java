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

import static com.velocitypowered.proxy.protocol.packet.brigadier.IntegerArgumentPropertySerializer.HAS_MAXIMUM;
import static com.velocitypowered.proxy.protocol.packet.brigadier.IntegerArgumentPropertySerializer.HAS_MINIMUM;
import static com.velocitypowered.proxy.protocol.packet.brigadier.IntegerArgumentPropertySerializer.getFlags;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.velocitypowered.api.network.ProtocolVersion;
import io.netty.buffer.ByteBuf;

/**
 * The {@code DoubleArgumentPropertySerializer} handles serialization and deserialization
 * of {@link DoubleArgumentType}, preserving optional minimum and maximum constraints.
 *
 * <p>This serializer is used when command arguments require bounded double values,
 * such as for coordinates, motion, or custom numeric inputs.</p>
 *
 * <p>The encoding uses a flags byte to denote the presence of minimum and/or
 * maximum values, followed by the corresponding doubles if present.</p>
 */
final class DoubleArgumentPropertySerializer implements ArgumentPropertySerializer<DoubleArgumentType> {

  /**
   * A shared singleton instance of the {@code DoubleArgumentPropertySerializer}.
   */
  static final DoubleArgumentPropertySerializer DOUBLE = new DoubleArgumentPropertySerializer();

  private DoubleArgumentPropertySerializer() {
  }

  @Override
  public DoubleArgumentType deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    byte flags = buf.readByte();
    double minimum = (flags & HAS_MINIMUM) != 0 ? buf.readDouble() : Double.MIN_VALUE;
    double maximum = (flags & HAS_MAXIMUM) != 0 ? buf.readDouble() : Double.MAX_VALUE;
    return DoubleArgumentType.doubleArg(minimum, maximum);
  }

  @Override
  public void serialize(final DoubleArgumentType object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
    boolean hasMinimum = Double.compare(object.getMinimum(), Double.MIN_VALUE) != 0;
    boolean hasMaximum = Double.compare(object.getMaximum(), Double.MAX_VALUE) != 0;
    byte flag = getFlags(hasMinimum, hasMaximum);

    buf.writeByte(flag);
    if (hasMinimum) {
      buf.writeDouble(object.getMinimum());
    }

    if (hasMaximum) {
      buf.writeDouble(object.getMaximum());
    }
  }
}
