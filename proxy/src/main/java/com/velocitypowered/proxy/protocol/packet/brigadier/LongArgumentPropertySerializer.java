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

import static com.velocitypowered.proxy.protocol.packet.brigadier.IntegerArgumentPropertySerializer.HAS_MAXIMUM;
import static com.velocitypowered.proxy.protocol.packet.brigadier.IntegerArgumentPropertySerializer.HAS_MINIMUM;
import static com.velocitypowered.proxy.protocol.packet.brigadier.IntegerArgumentPropertySerializer.getFlags;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.velocitypowered.api.network.ProtocolVersion;
import io.netty.buffer.ByteBuf;

/**
 * The {@code LongArgumentPropertySerializer} handles serialization and deserialization
 * of {@link LongArgumentType}, preserving optional minimum and maximum bounds.
 *
 * <p>This serializer is used for command arguments that accept long integer values,
 * which are useful for time, ticks, or large numeric ranges in Minecraft commands.</p>
 *
 * <p>Like other numeric serializers, it uses a single flag byte to encode the presence
 * of minimum and maximum values, followed by the respective long values when present.</p>
 */
final class LongArgumentPropertySerializer implements ArgumentPropertySerializer<LongArgumentType> {

  /**
   * A shared singleton instance of {@code LongArgumentPropertySerializer}.
   */
  static final LongArgumentPropertySerializer LONG = new LongArgumentPropertySerializer();

  private LongArgumentPropertySerializer() {
  }

  @Override
  public LongArgumentType deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    byte flags = buf.readByte();
    long minimum = (flags & HAS_MINIMUM) != 0 ? buf.readLong() : Long.MIN_VALUE;
    long maximum = (flags & HAS_MAXIMUM) != 0 ? buf.readLong() : Long.MAX_VALUE;
    return LongArgumentType.longArg(minimum, maximum);
  }

  @Override
  public void serialize(final LongArgumentType object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
    boolean hasMinimum = object.getMinimum() != Long.MIN_VALUE;
    boolean hasMaximum = object.getMaximum() != Long.MAX_VALUE;
    byte flag = getFlags(hasMinimum, hasMaximum);

    buf.writeByte(flag);
    if (hasMinimum) {
      buf.writeLong(object.getMinimum());
    }

    if (hasMaximum) {
      buf.writeLong(object.getMaximum());
    }
  }
}
