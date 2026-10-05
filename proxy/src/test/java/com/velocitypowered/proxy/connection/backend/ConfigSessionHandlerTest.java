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

package com.velocitypowered.proxy.connection.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.registry.DimensionInfo;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket;
import com.velocitypowered.proxy.tablist.InternalTabList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigSessionHandlerTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void synchronousPlayPacketsDuringConfigurationAcknowledgement(boolean buffer) throws Exception {
    ProtocolVersion version = ProtocolVersion.MINECRAFT_1_20_5;
    ProtocolUtils.Direction inbound = ProtocolUtils.Direction.CLIENTBOUND;
    ProtocolUtils.Direction outbound = ProtocolUtils.Direction.SERVERBOUND;
    VelocityServer server = mock(VelocityServer.class);
    ConnectedPlayer player = mock(ConnectedPlayer.class);
    when(player.getProtocolVersion()).thenReturn(version);
    when(player.getTabList()).thenReturn(mock(InternalTabList.class));
    when(player.getPlayerListHeader()).thenReturn(Component.empty());
    when(player.getPlayerListFooter()).thenReturn(Component.empty());
    VelocityServerConnection serverConn = mock(VelocityServerConnection.class);
    when(serverConn.getPlayer()).thenReturn(player);
    when(player.getConnectedServer()).thenReturn(serverConn);
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addLast(Connections.MINECRAFT_DECODER, new MinecraftDecoder(inbound));
    channel.pipeline().addLast(Connections.MINECRAFT_ENCODER, new MinecraftEncoder(outbound));
    MinecraftConnection connection = new MinecraftConnection(channel, server, null);
    channel.pipeline().addLast(Connections.HANDLER, connection);
    connection.setProtocolVersion(version);
    when(serverConn.getConnection()).thenReturn(connection);
    when(serverConn.ensureConnected()).thenReturn(connection);
    AtomicInteger received = new AtomicInteger();
    connection.addSessionHandler(StateRegistry.PLAY, new MinecraftSessionHandler() {
      @Override
      public boolean handle(JoinGamePacket packet) {
        received.incrementAndGet();
        return true;
      }
    });
    ConfigSessionHandler handler = new ConfigSessionHandler(server, serverConn,
        new CompletableFuture<>());
    connection.setActiveSessionHandler(StateRegistry.CONFIG, handler);
    AtomicInteger acknowledgements = new AtomicInteger();
    channel.pipeline().addBefore(Connections.MINECRAFT_ENCODER, "via-configuration-bridge",
        new ChannelOutboundHandlerAdapter() {
          @Override
          public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
              throws Exception {
            ByteBuf ack = (ByteBuf) msg;
            assertEquals(StateRegistry.CONFIG.getProtocolRegistry(outbound, version)
                .getPacketId(FinishedUpdatePacket.INSTANCE), ProtocolUtils.readVarInt(ack));
            assertFalse(ack.isReadable(), "configuration acknowledgement has no payload");
            acknowledgements.incrementAndGet();
            // ViaVersion's older-backend bridge releases JoinGame synchronously in this write.
            JoinGamePacket join = new JoinGamePacket();
            Field levels = JoinGamePacket.class.getDeclaredField("levelNames");
            levels.setAccessible(true);
            levels.set(join, ImmutableSet.of("minecraft:overworld"));
            join.setDimensionInfo(new DimensionInfo("minecraft:overworld", "minecraft:overworld",
                false, false, version));
            ByteBuf frame = Unpooled.buffer();
            ProtocolUtils.writeVarInt(frame,
                StateRegistry.PLAY.getProtocolRegistry(inbound, version).getPacketId(join));
            join.encode(frame, inbound, version);
            channel.writeInbound(frame);
            ctx.write(msg, promise);
          }
        });
    try {
      Method advance = ConfigSessionHandler.class.getDeclaredMethod("advanceBackendToPlay",
          boolean.class);
      advance.setAccessible(true);
      advance.invoke(handler, buffer);
      assertEquals(1, acknowledgements.get());
      assertEquals(buffer ? 0 : 1, received.get());
      connection.removePlayPacketQueueInboundHandler();
      assertEquals(1, received.get(), "JoinGame must reach PLAY exactly once");
      advance.invoke(handler, buffer);
      assertEquals(1, acknowledgements.get(), "acknowledge each configuration phase only once");
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void configurationPacketsUnknownToTheProxyStillReachThePlayer() {
    MinecraftConnection playerConnection = mock(MinecraftConnection.class);
    ConnectedPlayer player = mock(ConnectedPlayer.class);
    when(player.getConnection()).thenReturn(playerConnection);
    VelocityServerConnection serverConn = mock(VelocityServerConnection.class);
    when(serverConn.getPlayer()).thenReturn(player);
    ConfigSessionHandler handler = new ConfigSessionHandler(mock(VelocityServer.class), serverConn,
        new CompletableFuture<>());
    ByteBuf resetChat = Unpooled.buffer().writeByte(0x06);

    handler.handleUnknown(resetChat);

    verify(playerConnection).write(resetChat);
    assertEquals(2, resetChat.refCnt(), "retained for the write");
    resetChat.release(resetChat.refCnt());
  }
}
