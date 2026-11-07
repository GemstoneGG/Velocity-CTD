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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.brigadier.arguments.ArgumentType;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Registry-key arguments must be re-sent to the client with the same wire identifier the backend
 * used: {@code resource_or_tag} and {@code resource} share a payload format, but are distinct types.
 */
class RegistryArgumentRoundTripTest {

  @Test
  void registryArgumentsKeepTheirWireIdentifier() throws Exception {
    List<ProtocolVersion> versions = List.of(ProtocolVersion.MINECRAFT_1_19_3,
        ProtocolVersion.MINECRAFT_1_20_5, ProtocolVersion.MAXIMUM_VERSION);
    List<String> names = List.of("minecraft:resource_or_tag", "minecraft:resource_or_tag_key",
        "minecraft:resource", "minecraft:resource_key");

    for (ProtocolVersion version : versions) {
      for (String name : names) {
        ByteBuf in = Unpooled.buffer();
        ByteBuf out = Unpooled.buffer();
        try {
          ArgumentPropertyRegistry.writeIdentifier(in, identifier(name), version);
          ProtocolUtils.writeString(in, "minecraft:worldgen/biome");
          String expected = ByteBufUtil.hexDump(in);

          ArgumentType<?> type = ArgumentPropertyRegistry.deserialize(in, version);
          ArgumentPropertyRegistry.serialize(out, type, version);

          assertEquals(expected, ByteBufUtil.hexDump(out), name + " on " + version);
        } finally {
          in.release();
          out.release();
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static ArgumentIdentifier identifier(String name) throws Exception {
    Field field = ArgumentPropertyRegistry.class.getDeclaredField("byIdentifier");
    field.setAccessible(true);
    Map<ArgumentIdentifier, ?> byIdentifier = (Map<ArgumentIdentifier, ?>) field.get(null);
    return byIdentifier.keySet().stream()
        .filter(id -> id.getIdentifier().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("unknown argument type " + name));
  }
}
