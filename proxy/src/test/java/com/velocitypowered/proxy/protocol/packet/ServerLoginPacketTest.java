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

package com.velocitypowered.proxy.protocol.packet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServerLoginPacketTest {

  private static ByteBuf login(String name) {
    ByteBuf buf = Unpooled.buffer();
    ProtocolUtils.writeString(buf, name);
    ProtocolUtils.writeUuid(buf, UUID.randomUUID());
    return buf;
  }

  @ParameterizedTest
  @ValueSource(strings = {"Steve", ".BedrockName", "Bedrock Name", "§aName", "$Name%+", "_x_"})
  void acceptsPrintableNames(String name) {
    ServerLoginPacket packet = new ServerLoginPacket();
    assertDoesNotThrow(() -> packet.decode(login(name), Direction.SERVERBOUND,
        ProtocolVersion.MINECRAFT_1_21_4));
    assertEquals(name, packet.getUsername());
  }

  @ParameterizedTest
  @ValueSource(strings = {"a\nb", "a\rb", "a\tb", "a\u0000b", "a\u007fb", "a\u0085b", "\n"})
  void rejectsControlCharacters(String name) {
    assertThrows(QuietDecoderException.class, () -> new ServerLoginPacket()
        .decode(login(name), Direction.SERVERBOUND, ProtocolVersion.MINECRAFT_1_21_4));
  }
}
