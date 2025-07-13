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
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;

/**
 * A special {@link ArgumentPropertySerializer} implementation for mod-defined argument types
 * represented by {@link ModArgumentProperty}.
 *
 * <p>This serializer handles deserialization of mod-specific arguments that include a
 * {@link ArgumentIdentifier} and an arbitrary binary payload. These arguments are typically
 * used by modding platforms or extensions to define custom Brigadier-compatible types.</p>
 *
 * <p>Serialization is explicitly unsupported, as mod arguments are meant to be deserialized
 * only. The {@link ArgumentPropertyRegistry} handles raw passthrough of these types at runtime.</p>
 */
final class ModArgumentPropertySerializer implements ArgumentPropertySerializer<ModArgumentProperty> {

  /**
   * A shared singleton instance of the {@code ModArgumentPropertySerializer}.
   */
  static final ModArgumentPropertySerializer MOD = new ModArgumentPropertySerializer();

  private ModArgumentPropertySerializer() {
  }

  @Override
  public @NotNull ModArgumentProperty deserialize(final ByteBuf buf, final ProtocolVersion version) {
    ArgumentIdentifier identifier;
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      int idx = ProtocolUtils.readVarInt(buf);
      identifier = ArgumentIdentifier.id("crossstitch:identified_" + (idx < 0 ? "n" + (-idx) : idx),
          ArgumentIdentifier.mapSet(version, idx));
    } else {
      identifier = ArgumentIdentifier.id(ProtocolUtils.readString(buf));
    }

    byte[] extraData = ProtocolUtils.readByteArray(buf);
    return new ModArgumentProperty(identifier, Unpooled.wrappedBuffer(extraData));
  }

  @Override
  public void serialize(final ModArgumentProperty object, final ByteBuf buf, final ProtocolVersion version) {
    // This is special-cased by ArgumentPropertyRegistry
    throw new UnsupportedOperationException();
  }
}
