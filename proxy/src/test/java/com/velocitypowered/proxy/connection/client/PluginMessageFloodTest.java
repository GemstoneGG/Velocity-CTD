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

package com.velocitypowered.proxy.connection.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.BackendConnectionPhases;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultEventLoop;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginMessageFloodTest {

  private final DefaultEventLoop loop = new DefaultEventLoop();
  private VelocityServer server;
  private ConnectedPlayer player;
  private ClientPlaySessionHandler handler;

  private static ByteBuf wire(int payload) {
    ByteBuf buf = Unpooled.buffer();
    ProtocolUtils.writeString(buf, "t:c");
    buf.writeZero(payload);
    return buf;
  }

  @Test
  void serverboundPayloadIsCappedOnItsOwn() {
    assertDoesNotThrow(() -> new PluginMessagePacket().decode(wire(32767), Direction.SERVERBOUND,
        ProtocolVersion.MINECRAFT_1_21_4));
    assertThrows(Exception.class, () -> new PluginMessagePacket().decode(wire(32768),
        Direction.SERVERBOUND, ProtocolVersion.MINECRAFT_1_21_4));
    assertDoesNotThrow(() -> new PluginMessagePacket().decode(wire(200_000), Direction.CLIENTBOUND,
        ProtocolVersion.MINECRAFT_1_21_4));
  }

  @BeforeEach
  void setUp() {
    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    when(server.getChannelRegistrar().getFromId("t:c")).thenReturn(MinecraftChannelIdentifier.from(
        "t:c"));
    player = mock(ConnectedPlayer.class, RETURNS_DEEP_STUBS);
    when(player.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_4);
    when(player.getPhase()).thenReturn(ClientConnectionPhases.VANILLA);
    VelocityServerConnection serverConn = mock(VelocityServerConnection.class);
    MinecraftConnection backend = mock(MinecraftConnection.class);
    when(backend.getState()).thenReturn(StateRegistry.PLAY);
    when(backend.eventLoop()).thenReturn(loop);
    when(serverConn.getConnection()).thenReturn(backend);
    when(serverConn.getPhase()).thenReturn(BackendConnectionPhases.VANILLA);
    when(player.getConnectedServer()).thenReturn(serverConn);
    handler = new ClientPlaySessionHandler(server, player);
  }

  @AfterEach
  void tearDown() {
    loop.shutdownGracefully();
  }

  private void send(int payload) {
    PluginMessagePacket packet = new PluginMessagePacket("t:c", Unpooled.wrappedBuffer(
        new byte[payload]));
    handler.handle(packet);
    packet.release();
  }

  @Test
  void unfinishedEventsHitTheCountCap() {
    when(server.getEventManager().fire(any(PluginMessageEvent.class))).thenReturn(
        new CompletableFuture<>());
    for (int i = 0; i < 1024; i++) {
      send(1);
    }
    verify(player, never()).disconnect(any());
    send(1);
    verify(player, times(1)).disconnect(any());
    send(1);
    verify(server.getEventManager(), times(1024)).fire(any(PluginMessageEvent.class));
  }

  @Test
  void unfinishedEventsHitTheByteCap() {
    when(server.getEventManager().fire(any(PluginMessageEvent.class))).thenReturn(
        new CompletableFuture<>());
    for (int i = 0; i < 128; i++) {
      send(32767);
    }
    verify(player, never()).disconnect(any());
    send(32767);
    verify(player, times(1)).disconnect(any());
  }

  @Test
  void finishedEventsFreeTheirRoom() throws Exception {
    when(server.getEventManager().fire(any(PluginMessageEvent.class))).thenAnswer(
        inv -> CompletableFuture.completedFuture(inv.getArgument(0)));
    for (int i = 0; i < 5000; i++) {
      send(1000);
      loop.submit(() -> {}).sync();
    }
    verify(player, never()).disconnect(any());
  }

  private void sendDuringConfiguration(ClientConfigSessionHandler config, int payload) {
    PluginMessagePacket packet = new PluginMessagePacket("t:c", Unpooled.wrappedBuffer(
        new byte[payload]));
    config.handle(packet);
    packet.release();
  }

  @Test
  void unfinishedEventsDuringConfigurationHitTheCountCap() {
    ClientConfigSessionHandler config = new ClientConfigSessionHandler(server, player);
    when(server.getEventManager().fire(any(PluginMessageEvent.class))).thenReturn(
        new CompletableFuture<>());
    for (int i = 0; i < 1024; i++) {
      sendDuringConfiguration(config, 1);
    }
    verify(player, never()).disconnect(any());
    sendDuringConfiguration(config, 1);
    verify(player, times(1)).disconnect(any());
    sendDuringConfiguration(config, 1);
    verify(server.getEventManager(), times(1024)).fire(any(PluginMessageEvent.class));
  }

  @Test
  void unfinishedEventsDuringConfigurationHitTheByteCap() {
    ClientConfigSessionHandler config = new ClientConfigSessionHandler(server, player);
    when(server.getEventManager().fire(any(PluginMessageEvent.class))).thenReturn(
        new CompletableFuture<>());
    for (int i = 0; i < 128; i++) {
      sendDuringConfiguration(config, 32767);
    }
    verify(player, never()).disconnect(any());
    sendDuringConfiguration(config, 32767);
    verify(player, times(1)).disconnect(any());
  }

  @Test
  void finishedEventsDuringConfigurationFreeTheirRoom() throws Exception {
    ClientConfigSessionHandler config = new ClientConfigSessionHandler(server, player);
    when(player.getConnection().eventLoop()).thenReturn(loop);
    when(server.getEventManager().fire(any(PluginMessageEvent.class))).thenAnswer(
        inv -> CompletableFuture.completedFuture(inv.getArgument(0)));
    for (int i = 0; i < 5000; i++) {
      sendDuringConfiguration(config, 1000);
      loop.submit(() -> {}).sync();
    }
    verify(player, never()).disconnect(any());
  }
}
