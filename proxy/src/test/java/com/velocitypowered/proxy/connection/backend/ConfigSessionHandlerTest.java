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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import com.velocitypowered.proxy.protocol.packet.chat.ChatAcknowledgementPacket;
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket;
import com.velocitypowered.proxy.tablist.InternalTabList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfigSessionHandlerTest {

  private static final ProtocolVersion VERSION = ProtocolVersion.MINECRAFT_1_20_5;
  private static final ProtocolUtils.Direction INBOUND = ProtocolUtils.Direction.CLIENTBOUND;
  private static final ProtocolUtils.Direction OUTBOUND = ProtocolUtils.Direction.SERVERBOUND;

  @ParameterizedTest
  @CsvSource({"true, false", "true, true", "false, false", "false, true"})
  void playPacketsAnsweringTheAcknowledgementAreHandledInPlay(boolean reconfiguring,
      boolean buffer) throws Exception {
    Backend backend = new Backend(reconfiguring);
    AtomicInteger acknowledgements = new AtomicInteger();
    backend.channel.pipeline().addBefore(Connections.MINECRAFT_ENCODER, "answers-synchronously",
        new ChannelOutboundHandlerAdapter() {
          @Override
          public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ByteBuf acknowledgement = (ByteBuf) msg;
            assertEquals(configPacketId(FinishedUpdatePacket.INSTANCE),
                ProtocolUtils.readVarInt(acknowledgement.duplicate()));
            acknowledgements.incrementAndGet();
            backend.channel.writeInbound(encodedJoinGame());
            ctx.write(msg, promise);
          }
        });

    try {
      backend.advance(buffer);

      assertEquals(1, acknowledgements.get());
      assertEquals(buffer ? 0 : 1, backend.handled.size());
      backend.connection.removePlayPacketQueueInboundHandler();
      assertEquals(1, backend.handled.size(), "JoinGame must be handled exactly once");
      assertInstanceOf(JoinGamePacket.class, backend.handled.getFirst().message());
      if (reconfiguring) {
        assertSame(backend.playHandler, backend.handled.getFirst().handler());
      } else {
        assertInstanceOf(TransitionSessionHandler.class, backend.handled.getFirst().handler());
      }
      assertNull(backend.channel.pipeline().get(Connections.INBOUND_HOLD));

      backend.advance(buffer);
      assertEquals(1, acknowledgements.get(), "acknowledge each configuration phase only once");
    } finally {
      backend.channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @CsvSource({"true", "false"})
  void nothingQueuedForPlayOvertakesTheAcknowledgement(boolean reconfiguring) throws Exception {
    Backend backend = new Backend(reconfiguring);
    ChatAcknowledgementPacket queued = new ChatAcknowledgementPacket(7);
    backend.connection.write(queued);

    try {
      backend.advance(false);

      List<String> written = new ArrayList<>();
      Object msg;
      while ((msg = backend.channel.readOutbound()) != null) {
        written.add(ByteBufUtil.hexDump((ByteBuf) msg));
        ReferenceCountUtil.release(msg);
      }
      ByteBuf acknowledgement = Unpooled.buffer();
      ProtocolUtils.writeVarInt(acknowledgement, configPacketId(FinishedUpdatePacket.INSTANCE));
      ByteBuf chatAcknowledgement = Unpooled.buffer();
      ProtocolUtils.writeVarInt(chatAcknowledgement,
          StateRegistry.PLAY.getProtocolRegistry(OUTBOUND, VERSION).getPacketId(queued));
      queued.encode(chatAcknowledgement, OUTBOUND, VERSION);
      assertEquals(List.of(ByteBufUtil.hexDump(acknowledgement),
          ByteBufUtil.hexDump(chatAcknowledgement)), written);
      acknowledgement.release();
      chatAcknowledgement.release();
    } finally {
      backend.channel.finishAndReleaseAll();
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

  private static int configPacketId(MinecraftPacket packet) {
    return StateRegistry.CONFIG.getProtocolRegistry(OUTBOUND, VERSION).getPacketId(packet);
  }

  private static ByteBuf encodedJoinGame() {
    JoinGamePacket joinGame = new JoinGamePacket();
    try {
      Field levelNames = JoinGamePacket.class.getDeclaredField("levelNames");
      levelNames.setAccessible(true);
      levelNames.set(joinGame, ImmutableSet.of("minecraft:overworld"));
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
    joinGame.setDimensionInfo(new DimensionInfo("minecraft:overworld", "minecraft:overworld",
        false, false, VERSION));
    ByteBuf frame = Unpooled.buffer();
    ProtocolUtils.writeVarInt(frame,
        StateRegistry.PLAY.getProtocolRegistry(INBOUND, VERSION).getPacketId(joinGame));
    joinGame.encode(frame, INBOUND, VERSION);
    return frame;
  }

  private record Handled(Object message, MinecraftSessionHandler handler) {
  }

  private static final class Backend {

    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final MinecraftConnection connection;
    private final MinecraftSessionHandler playHandler = new MinecraftSessionHandler() {
    };
    private final List<Handled> handled = new ArrayList<>();
    private final ConfigSessionHandler configHandler;

    private Backend(boolean reconfiguring) {
      ConnectedPlayer player = mock(ConnectedPlayer.class);
      when(player.getProtocolVersion()).thenReturn(VERSION);
      when(player.getTabList()).thenReturn(mock(InternalTabList.class));
      when(player.getPlayerListHeader()).thenReturn(Component.empty());
      when(player.getPlayerListFooter()).thenReturn(Component.empty());
      VelocityServerConnection serverConn = mock(VelocityServerConnection.class);
      when(serverConn.getPlayer()).thenReturn(player);
      if (reconfiguring) {
        when(player.getConnectedServer()).thenReturn(serverConn);
      }

      channel.pipeline().addLast(Connections.MINECRAFT_DECODER, new MinecraftDecoder(INBOUND));
      channel.pipeline().addLast(Connections.MINECRAFT_ENCODER, new MinecraftEncoder(OUTBOUND));
      VelocityServer server = mock(VelocityServer.class);
      connection = new MinecraftConnection(channel, server, null);
      channel.pipeline().addLast("records-handling", new ChannelInboundHandlerAdapter() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
          handled.add(new Handled(msg, connection.getActiveSessionHandler()));
        }
      });
      channel.pipeline().addLast(Connections.HANDLER, connection);
      connection.setProtocolVersion(VERSION);
      when(serverConn.getConnection()).thenReturn(connection);
      when(serverConn.ensureConnected()).thenReturn(connection);

      connection.addSessionHandler(StateRegistry.PLAY, playHandler);
      configHandler = new ConfigSessionHandler(server, serverConn, new CompletableFuture<>());
      connection.setActiveSessionHandler(StateRegistry.CONFIG, configHandler);
    }

    private void advance(boolean buffer) throws ReflectiveOperationException {
      Method advance = ConfigSessionHandler.class.getDeclaredMethod("advanceBackendToPlay",
          boolean.class);
      advance.setAccessible(true);
      advance.invoke(configHandler, buffer);
    }
  }
}
