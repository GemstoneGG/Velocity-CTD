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

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * Serializer for {@link StringArgumentType}, which supports multiple formats of string input
 * in Minecraft commands (e.g., single words, quoted phrases, or greedy strings).
 *
 * <p>This serializer handles encoding and decoding of the argument type format using a compact
 * integer representation. The encoded type is one of the following:</p>
 *
 * <ul>
 *   <li>{@code 0} - {@link StringArgumentType#word()} (single word)</li>
 *   <li>{@code 1} - {@link StringArgumentType#string()} (quotable phrase)</li>
 *   <li>{@code 2} - {@link StringArgumentType#greedyString()} (greedy/remaining input)</li>
 * </ul>
 */
final class StringArgumentPropertySerializer implements ArgumentPropertySerializer<StringArgumentType> {

  public static final ArgumentPropertySerializer<StringArgumentType> STRING = new StringArgumentPropertySerializer();

  private StringArgumentPropertySerializer() {
  }

  @Override
  public StringArgumentType deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    int type = ProtocolUtils.readVarInt(buf);
    return switch (type) {
      case 0 -> StringArgumentType.word();
      case 1 -> StringArgumentType.string();
      case 2 -> StringArgumentType.greedyString();
      default -> throw new IllegalArgumentException("Invalid string argument type " + type);
    };
  }

  @Override
  public void serialize(final StringArgumentType object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
    switch (object.getType()) {
      case SINGLE_WORD -> ProtocolUtils.writeVarInt(buf, 0);
      case QUOTABLE_PHRASE -> ProtocolUtils.writeVarInt(buf, 1);
      case GREEDY_PHRASE -> ProtocolUtils.writeVarInt(buf, 2);
      default -> throw new IllegalArgumentException("Invalid string argument type " + object.getType());
    }
  }
}
