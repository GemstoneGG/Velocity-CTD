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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent.LoginStatus;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginStatusTest {

  private VelocityServer server;
  private PlayerRegistry registry;
  private final AtomicReference<DisconnectEvent> fired = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    when(server.getEventManager().fire(any(DisconnectEvent.class))).thenAnswer(inv -> {
      fired.set(inv.getArgument(0));
      return CompletableFuture.completedFuture(inv.getArgument(0));
    });
    registry = new PlayerRegistry(server);
  }

  private ConnectedPlayer loggedIn() throws Exception {
    MinecraftConnection connection = mock(MinecraftConnection.class, RETURNS_DEEP_STUBS);
    when(connection.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_4);
    when(connection.isClosed()).thenReturn(false);
    when(connection.isKnownDisconnect()).thenReturn(false);
    ConnectedPlayer player = new ConnectedPlayer(server,
        new GameProfile(UUID.randomUUID(), "Tester", List.of()), connection, null, null, true,
        HandshakeIntent.LOGIN, null);
    assertTrue(registry.registerConnection(player).get(5, TimeUnit.SECONDS));
    player.markLoginEventFired();
    registry.finalizeLogin(player);
    return player;
  }

  private LoginStatus quit(ConnectedPlayer player) throws Exception {
    registry.unregisterConnection(player).get(5, TimeUnit.SECONDS);
    return fired.get().getLoginStatus();
  }

  @Test
  void backendKickAfterJoinIsSuccessfulLogin() throws Exception {
    ConnectedPlayer player = loggedIn();
    player.setConnectedServer(mock(VelocityServerConnection.class, RETURNS_DEEP_STUBS));
    player.setConnectedServer(null);
    assertEquals(LoginStatus.SUCCESSFUL_LOGIN, quit(player));
  }

  @Test
  void quitWhileConnectedIsSuccessfulLogin() throws Exception {
    ConnectedPlayer player = loggedIn();
    player.setConnectedServer(mock(VelocityServerConnection.class, RETURNS_DEEP_STUBS));
    assertEquals(LoginStatus.SUCCESSFUL_LOGIN, quit(player));
  }

  @Test
  void quitMidSwitchIsSuccessfulLogin() throws Exception {
    ConnectedPlayer player = loggedIn();
    player.setConnectedServer(mock(VelocityServerConnection.class, RETURNS_DEEP_STUBS));
    player.setConnectedServer(null);
    player.setConnectedServer(mock(VelocityServerConnection.class, RETURNS_DEEP_STUBS));
    assertEquals(LoginStatus.SUCCESSFUL_LOGIN, quit(player));
  }

  @Test
  void neverJoinedIsPreServerJoin() throws Exception {
    ConnectedPlayer player = loggedIn();
    assertEquals(LoginStatus.PRE_SERVER_JOIN, quit(player));
  }
}
