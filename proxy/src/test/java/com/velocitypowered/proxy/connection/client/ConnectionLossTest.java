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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.DynamicFallbackFilter;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.plugin.VelocityPluginManager;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * Covers {@code advanced.reconnect-to-last-server}: which connection failures count as a lost
 * connection, and how the server the player was on is used when they log in again.
 */
class ConnectionLossTest {

  private VelocityServer server;
  private VelocityConfiguration config;
  private PlayerRegistry registry;
  private EmbeddedChannel channel;
  private ConnectedPlayer player;
  private VelocityRegisteredServer lobby;
  private VelocityRegisteredServer survival;

  @BeforeEach
  void setUp() throws Exception {
    server = mock(VelocityServer.class);
    config = mock(VelocityConfiguration.class);
    when(server.getConfiguration()).thenReturn(config);
    when(config.getReconnectToLastServer()).thenReturn(120_000);
    when(config.getAttemptConnectionOrder()).thenReturn(List.of("lobby", "survival"));
    when(config.getDynamicFallbackFilter()).thenReturn(DynamicFallbackFilter.FIRST_AVAILABLE);
    VelocityPluginManager plugins = mock(VelocityPluginManager.class);
    PluginContainer container = mock(PluginContainer.class);
    when(server.getPluginManager()).thenReturn(plugins);
    when(plugins.ensurePluginContainer(ArgumentMatchers.any())).thenReturn(container);
    when(container.getExecutorService()).thenReturn(mock(ExecutorService.class));
    registry = new PlayerRegistry(server);
    when(server.getPlayerRegistry()).thenReturn(registry);
    lobby = registeredServer(server, "lobby");
    survival = registeredServer(server, "survival");

    channel = new EmbeddedChannel();
    channel.pipeline().addLast("decoder", new MinecraftDecoder(ProtocolUtils.Direction.SERVERBOUND));
    channel.pipeline().addLast("encoder", new MinecraftEncoder(ProtocolUtils.Direction.CLIENTBOUND));
    MinecraftConnection connection = new MinecraftConnection(channel, server, null);
    connection.setProtocolVersion(ProtocolVersion.MINECRAFT_1_21_4);
    connection.setState(StateRegistry.PLAY);
    channel.pipeline().addLast(Connections.HANDLER, connection);
    player = new ConnectedPlayer(server, new GameProfile(UUID.randomUUID(), "Steve", List.of()),
        connection, null, null, true, HandshakeIntent.LOGIN, null);
    connection.setAssociation(player);

    ServerInfo survivalInfo = survival.getServerInfo();
    VelocityServerConnection backend = mock(VelocityServerConnection.class);
    when(backend.getServer()).thenReturn(survival);
    when(backend.getServerInfo()).thenReturn(survivalInfo);
    Field connectedServer = ConnectedPlayer.class.getDeclaredField("connectedServer");
    connectedServer.setAccessible(true);
    connectedServer.set(player, backend);
  }

  private static VelocityRegisteredServer registeredServer(VelocityServer server, String name) {
    VelocityRegisteredServer registered = mock(VelocityRegisteredServer.class);
    when(registered.getServerInfo())
        .thenReturn(new ServerInfo(name, InetSocketAddress.createUnresolved(name, 25565)));
    when(server.getServer(name)).thenReturn(Optional.of(registered));
    return registered;
  }

  @Test
  void readTimeoutSendsThePlayerBackToTheirServer() {
    channel.pipeline().fireExceptionCaught(ReadTimeoutException.INSTANCE);

    assertFalse(channel.isOpen(), "the connection is still closed as before");
    assertEquals(Optional.of(survival), registry.nextInitialServer(player));
    assertEquals(Optional.of(lobby), registry.nextInitialServer(player),
        "used once; the configured order follows, without the server already tried");
    assertEquals(Optional.empty(), registry.nextInitialServer(player));
  }

  @Test
  void brokenSocketSendsThePlayerBackToTheirServer() {
    channel.pipeline().fireExceptionCaught(new IOException("Connection reset by peer"));
    assertEquals(Optional.of(survival), registry.nextInitialServer(player));
  }

  @Test
  void otherErrorsAndCleanClosesUseTheConfiguredOrder() {
    channel.pipeline().fireExceptionCaught(new IllegalStateException("bad packet"));
    channel.close();
    assertEquals(Optional.of(lobby), registry.nextInitialServer(player));
  }

  @Test
  void nothingIsRememberedWhenDisabled() {
    when(config.getReconnectToLastServer()).thenReturn(0);
    channel.pipeline().fireExceptionCaught(new IOException("Connection reset by peer"));
    assertEquals(Optional.of(lobby), registry.nextInitialServer(player));
  }

  @Test
  void removedServerIsSkipped() {
    channel.pipeline().fireExceptionCaught(new IOException("Connection reset by peer"));
    when(config.getAttemptConnectionOrder()).thenReturn(List.of("lobby"));
    when(server.getServer("survival")).thenReturn(Optional.empty());
    assertEquals(Optional.of(lobby), registry.nextInitialServer(player));
  }

  @Test
  void onlySilentSessionsInGameAreStale() {
    assertTrue(PlayerRegistry.isStale(StateRegistry.PLAY, 5_000, 5_000));
    assertTrue(PlayerRegistry.isStale(StateRegistry.PLAY, 60_000, 5_000));

    assertFalse(PlayerRegistry.isStale(StateRegistry.PLAY, 4_999, 5_000), "still sending recently");
    assertFalse(PlayerRegistry.isStale(StateRegistry.CONFIG, 60_000, 5_000), "may be quiet while configuring");
    assertFalse(PlayerRegistry.isStale(StateRegistry.PLAY, -1, 5_000), "idle time unknown");
    assertFalse(PlayerRegistry.isStale(StateRegistry.PLAY, 60_000, 0), "takeover disabled");
  }
}
