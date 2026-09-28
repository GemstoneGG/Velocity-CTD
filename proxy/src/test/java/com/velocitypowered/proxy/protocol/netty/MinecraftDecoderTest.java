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

package com.velocitypowered.proxy.protocol.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.config.StartUpdatePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class MinecraftDecoderTest {

  private static final ProtocolVersion VERSION = ProtocolVersion.MAXIMUM_VERSION;
  private static final ProtocolUtils.Direction CLIENTBOUND = ProtocolUtils.Direction.CLIENTBOUND;

  @Test
  void decodesPacketsAfterStartConfigurationInConfigState() {
    MinecraftDecoder decoder = new MinecraftDecoder(CLIENTBOUND);
    decoder.setState(StateRegistry.PLAY);
    decoder.setProtocolVersion(VERSION);
    EmbeddedChannel channel = new EmbeddedChannel(decoder);

    KeepAlivePacket keepAlive = new KeepAlivePacket();
    keepAlive.setRandomId(42);
    channel.writeInbound(frame(StateRegistry.PLAY, StartUpdatePacket.INSTANCE),
        frame(StateRegistry.CONFIG, keepAlive));

    assertInstanceOf(StartUpdatePacket.class, channel.readInbound());
    KeepAlivePacket decoded = assertInstanceOf(KeepAlivePacket.class, channel.readInbound());
    assertEquals(42, decoded.getRandomId());
    channel.finishAndReleaseAll();
  }

  @Test
  void configurationPacketsSentBeforeTheSwitchAreDroppedExceptDisconnects() {
    MinecraftDecoder decoder = new MinecraftDecoder(CLIENTBOUND);
    decoder.setState(StateRegistry.CONFIG);
    decoder.setProtocolVersion(VERSION);
    KeepAlivePacket playKeepAlive = new KeepAlivePacket();
    playKeepAlive.setRandomId(7);
    decoder.awaitState(StateRegistry.CONFIG, StateRegistry.PLAY,
        StateRegistry.PLAY.getProtocolRegistry(CLIENTBOUND, VERSION).getPacketId(playKeepAlive));
    EmbeddedChannel channel = new EmbeddedChannel(decoder);

    KeepAlivePacket staleKeepAlive = new KeepAlivePacket();
    staleKeepAlive.setRandomId(42);
    channel.writeInbound(frame(StateRegistry.CONFIG, staleKeepAlive),
        frame(StateRegistry.CONFIG,
            DisconnectPacket.create(Component.text("kicked"), VERSION, StateRegistry.CONFIG)),
        frame(StateRegistry.PLAY, playKeepAlive));

    assertInstanceOf(DisconnectPacket.class, channel.readInbound());
    KeepAlivePacket decoded = assertInstanceOf(KeepAlivePacket.class, channel.readInbound());
    assertEquals(7, decoded.getRandomId(), "the first PLAY packet switches the decoder");
    assertNull(channel.readInbound(), "the stale configuration keepalive is dropped");
    channel.finishAndReleaseAll();
  }

  private static ByteBuf frame(StateRegistry state, MinecraftPacket packet) {
    ByteBuf buf = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buf,
        state.getProtocolRegistry(CLIENTBOUND, VERSION).getPacketId(packet));
    packet.encode(buf, CLIENTBOUND, VERSION);
    return buf;
  }
}
