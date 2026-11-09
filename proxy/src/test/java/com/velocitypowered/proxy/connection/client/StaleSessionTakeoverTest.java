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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.DynamicFallbackFilter;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.network.netty.VelocityReadTimeoutHandler;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers {@code advanced.stale-session-takeover}: a player whose connection dropped without the
 * proxy noticing logs in again while their previous session is still registered.
 */
class StaleSessionTakeoverTest {

  private static final UUID PLAYER_ID = UUID.randomUUID();

  private VelocityServer server;
  private VelocityConfiguration config;
  private PlayerRegistry registry;
  private VelocityRegisteredServer survival;
  private EmbeddedChannel oldChannel;
  private ConnectedPlayer oldSession;

  @BeforeEach
  void setUp() throws Exception {
    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    when(server.getEventManager().fire(any(DisconnectEvent.class)))
        .thenAnswer(inv -> CompletableFuture.completedFuture(inv.getArgument(0)));
    config = mock(VelocityConfiguration.class);
    when(server.getConfiguration()).thenReturn(config);
    when(config.getStaleSessionTakeover()).thenReturn(5_000);
    when(config.getReconnectToLastServer()).thenReturn(120_000);
    when(config.getAttemptConnectionOrder()).thenReturn(List.of("lobby"));
    when(config.getDynamicFallbackFilter()).thenReturn(DynamicFallbackFilter.FIRST_AVAILABLE);
    ServerInfo survivalInfo =
        new ServerInfo("survival", InetSocketAddress.createUnresolved("survival", 25565));
    survival = mock(VelocityRegisteredServer.class);
    when(survival.getServerInfo()).thenReturn(survivalInfo);
    when(server.getServer("survival")).thenReturn(Optional.of(survival));
    when(server.getServer("lobby")).thenReturn(Optional.empty());
    registry = new PlayerRegistry(server);
    when(server.getPlayerRegistry()).thenReturn(registry);

    oldChannel = new EmbeddedChannel();
    oldChannel.freezeTime();
    oldChannel.pipeline().addLast(Connections.READ_TIMEOUT,
        new VelocityReadTimeoutHandler(30, TimeUnit.SECONDS));
    oldChannel.pipeline().addLast("decoder", new MinecraftDecoder(ProtocolUtils.Direction.SERVERBOUND));
    oldChannel.pipeline().addLast("encoder", new MinecraftEncoder(ProtocolUtils.Direction.CLIENTBOUND));
    MinecraftConnection connection = new MinecraftConnection(oldChannel, server, null);
    connection.setProtocolVersion(ProtocolVersion.MINECRAFT_1_21_4);
    connection.setState(StateRegistry.PLAY);
    oldChannel.pipeline().addLast(Connections.HANDLER, connection);
    set(MinecraftConnection.class, connection, "remoteAddress", new InetSocketAddress("10.0.0.1", 50000));
    oldSession = new ConnectedPlayer(server, new GameProfile(PLAYER_ID, "Steve", List.of()),
        connection, null, null, true, HandshakeIntent.LOGIN, null);
    connection.setAssociation(oldSession);
    assertTrue(await(registry.registerConnection(oldSession)));
    oldSession.markLoginEventFired();
    registry.finalizeLogin(oldSession);

    VelocityServerConnection backend = mock(VelocityServerConnection.class);
    when(backend.getServer()).thenReturn(survival);
    when(backend.getServerInfo()).thenReturn(survivalInfo);
    set(ConnectedPlayer.class, oldSession, "connectedServer", backend);
  }

  private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private ConnectedPlayer newLogin(boolean onlineMode, String address) {
    MinecraftConnection connection = mock(MinecraftConnection.class, RETURNS_DEEP_STUBS);
    when(connection.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_4);
    when(connection.isClosed()).thenReturn(false);
    when(connection.getRemoteAddress()).thenReturn(new InetSocketAddress(address, 50001));
    return new ConnectedPlayer(server, new GameProfile(PLAYER_ID, "Steve", List.of()), connection,
        null, null, onlineMode, HandshakeIntent.LOGIN, null);
  }

  /** Runs the old session's event loop until {@code future} completes. */
  private <T> T await(CompletableFuture<T> future) throws Exception {
    for (int i = 0; i < 100 && !future.isDone(); i++) {
      oldChannel.runPendingTasks();
    }
    return future.get(5, TimeUnit.SECONDS);
  }

  @Test
  void silentSessionIsReplacedAndThePlayerGoesBackToTheirServer() throws Exception {
    oldChannel.advanceTimeBy(6, TimeUnit.SECONDS);
    ConnectedPlayer newSession = newLogin(true, "10.0.0.2");

    assertTrue(await(registry.registerConnection(newSession)), "the new login is accepted");
    assertSame(newSession, registry.getPlayer(PLAYER_ID).orElseThrow());
    assertFalse(oldChannel.isActive(), "the stale session is closed");
    assertEquals(Optional.of(survival), registry.nextInitialServer(newSession));
  }

  @Test
  void liveSessionIsKept() throws Exception {
    oldChannel.advanceTimeBy(1, TimeUnit.SECONDS);

    assertFalse(await(registry.registerConnection(newLogin(true, "10.0.0.2"))),
        "a client that sent something a second ago is still there");
    assertSame(oldSession, registry.getPlayer(PLAYER_ID).orElseThrow());
    assertTrue(oldChannel.isActive());
  }

  @Test
  void offlineLoginMustComeFromTheSameAddress() throws Exception {
    oldChannel.advanceTimeBy(6, TimeUnit.SECONDS);

    assertFalse(await(registry.registerConnection(newLogin(false, "10.0.0.2"))),
        "an unauthenticated login from elsewhere could be anyone");
    assertSame(oldSession, registry.getPlayer(PLAYER_ID).orElseThrow());

    assertTrue(await(registry.registerConnection(newLogin(false, "10.0.0.1"))));
  }

  @Test
  void disabledTakeoverRefusesTheLoginAsBefore() throws Exception {
    when(config.getStaleSessionTakeover()).thenReturn(0);
    oldChannel.advanceTimeBy(6, TimeUnit.SECONDS);

    assertFalse(await(registry.registerConnection(newLogin(true, "10.0.0.2"))));
    assertSame(oldSession, registry.getPlayer(PLAYER_ID).orElseThrow());
  }
}
