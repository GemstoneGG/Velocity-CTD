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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.LoginPhaseConnection.MessageConsumer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.player.resourcepack.ResourcePackTransfer;
import com.velocitypowered.proxy.protocol.packet.LoginAcknowledgedPacket;
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponsePacket;
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket;
import com.velocitypowered.proxy.protocol.packet.ServerboundCookieResponsePacket;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A client that has not logged in may only send the login packets the proxy is waiting for. Any
 * other one closes the connection, since an ignored packet still counts as activity and would let
 * a client hold its connection open forever without logging in.
 */
class InitialLoginSessionHandlerTest {

  private VelocityServer server;
  private MinecraftConnection connection;
  private InitialInboundConnection delegate;
  private LoginInboundConnection inbound;

  @BeforeEach
  void setUp() {
    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    when(server.getEventManager().fire(any(PreLoginEvent.class)))
        .thenReturn(new CompletableFuture<>());
    connection = mock(MinecraftConnection.class);
    when(connection.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_4);
    when(connection.eventLoop()).thenReturn(mock(EventLoop.class));
    delegate = mock(InitialInboundConnection.class);
    when(delegate.getConnection()).thenReturn(connection);
    when(delegate.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_4);
    inbound = new LoginInboundConnection(delegate);
  }

  private InitialLoginSessionHandler handler(HandshakeIntent intent) {
    when(delegate.getHandshakeIntent()).thenReturn(intent);
    InitialLoginSessionHandler handler =
        new InitialLoginSessionHandler(server, connection, inbound);
    handler.activated();
    return handler;
  }

  @Test
  void unsolicitedPluginResponseClosesTheConnection() {
    handler(HandshakeIntent.LOGIN)
        .handle(new LoginPluginResponsePacket(7, true, Unpooled.EMPTY_BUFFER));

    verify(connection).close(true);
  }

  @Test
  void answerToSentQueryIsAccepted() {
    InitialLoginSessionHandler handler = handler(HandshakeIntent.LOGIN);
    MessageConsumer consumer = mock(MessageConsumer.class);
    inbound.sendLoginPluginMessage(MinecraftChannelIdentifier.create("test", "query"), new byte[0],
        consumer);

    handler.handle(new LoginPluginResponsePacket(1, true, Unpooled.EMPTY_BUFFER));

    verify(consumer).onMessageResponse(any());
    verify(connection, never()).close(anyBoolean());
  }

  @Test
  void answeringTheSameQueryTwiceClosesTheConnection() {
    InitialLoginSessionHandler handler = handler(HandshakeIntent.LOGIN);
    inbound.sendLoginPluginMessage(MinecraftChannelIdentifier.create("test", "query"), new byte[0],
        mock(MessageConsumer.class));

    handler.handle(new LoginPluginResponsePacket(1, true, Unpooled.EMPTY_BUFFER));
    verify(connection, never()).close(anyBoolean());

    handler.handle(new LoginPluginResponsePacket(1, true, Unpooled.EMPTY_BUFFER));
    verify(connection).close(true);
  }

  @Test
  void loginAcknowledgementBeforeLoginSuccessClosesTheConnection() {
    handler(HandshakeIntent.LOGIN).handle(new LoginAcknowledgedPacket());

    verify(connection).close(true);
  }

  @Test
  void cookieThatWasNotRequestedClosesTheConnection() {
    handler(HandshakeIntent.LOGIN).handle(
        new ServerboundCookieResponsePacket(ResourcePackTransfer.APPLIED_RESOURCE_PACKS_KEY, null));

    verify(connection).close(true);
  }

  @Test
  void cookieWithAnotherKeyClosesTheConnection() {
    handler(HandshakeIntent.TRANSFER).handle(
        new ServerboundCookieResponsePacket(Key.key("test", "other"), new byte[0]));

    verify(connection).close(true);
  }

  @Test
  void requestedCookieIsAcceptedOnce() {
    InitialLoginSessionHandler handler = handler(HandshakeIntent.TRANSFER);
    ServerboundCookieResponsePacket cookie =
        new ServerboundCookieResponsePacket(ResourcePackTransfer.APPLIED_RESOURCE_PACKS_KEY, null);

    handler.handle(cookie);
    verify(connection, never()).close(anyBoolean());

    handler.handle(cookie);
    verify(connection).close(true);
  }

  @Test
  void repeatedLoginStartIsNotHandledAgain() {
    InitialLoginSessionHandler handler = handler(HandshakeIntent.LOGIN);

    handler.handle(new ServerLoginPacket("Player", (UUID) null));
    handler.handle(new ServerLoginPacket("Player", (UUID) null));

    verify(server.getEventManager(), times(1)).fire(any(PreLoginEvent.class));
    verify(connection).close(true);
  }
}
