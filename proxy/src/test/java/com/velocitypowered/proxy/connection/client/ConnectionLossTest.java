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

import com.velocityctd.proxy.reconnect.LastServerTracker;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.plugin.VelocityPluginManager;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
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
 * Covers what feeds {@code advanced.reconnect-to-last-server} and
 * {@code advanced.stale-session-takeover}: which disconnects count as a lost connection, what is
 * remembered for them, and which silent sessions count as stale.
 */
class ConnectionLossTest {

  private LastServerTracker tracker;
  private VelocityConfiguration config;
  private EmbeddedChannel channel;
  private ConnectedPlayer player;

  @BeforeEach
  void setUp() throws Exception {
    VelocityServer server = mock(VelocityServer.class);
    config = mock(VelocityConfiguration.class);
    tracker = new LastServerTracker();
    when(server.getConfiguration()).thenReturn(config);
    when(server.getLastServerTracker()).thenReturn(tracker);
    when(config.getReconnectToLastServer()).thenReturn(120_000);
    VelocityPluginManager plugins = mock(VelocityPluginManager.class);
    PluginContainer container = mock(PluginContainer.class);
    when(server.getPluginManager()).thenReturn(plugins);
    when(plugins.ensurePluginContainer(ArgumentMatchers.any())).thenReturn(container);
    when(container.getExecutorService()).thenReturn(mock(ExecutorService.class));

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

    VelocityServerConnection backend = mock(VelocityServerConnection.class);
    when(backend.getServerInfo())
        .thenReturn(new ServerInfo("survival", InetSocketAddress.createUnresolved("survival", 25565)));
    Field connectedServer = ConnectedPlayer.class.getDeclaredField("connectedServer");
    connectedServer.setAccessible(true);
    connectedServer.set(player, backend);
  }

  private boolean connectionLost() throws Exception {
    Field field = ConnectedPlayer.class.getDeclaredField("connectionLost");
    field.setAccessible(true);
    return field.getBoolean(player);
  }

  @Test
  void readTimeoutCountsAsConnectionLoss() throws Exception {
    channel.pipeline().fireExceptionCaught(ReadTimeoutException.INSTANCE);
    assertTrue(connectionLost());
  }

  @Test
  void brokenSocketCountsAsConnectionLoss() throws Exception {
    channel.pipeline().fireExceptionCaught(new IOException("Connection reset by peer"));
    assertTrue(connectionLost());
  }

  @Test
  void otherErrorsAndCleanClosesAreNot() throws Exception {
    channel.pipeline().fireExceptionCaught(new IllegalStateException("bad packet"));
    assertFalse(connectionLost());

    channel.close();
    assertFalse(connectionLost());
  }

  @Test
  void remembersTheServerThePlayerWasOn() {
    player.rememberServerForReconnect();
    assertEquals(Optional.of("survival"), tracker.consume(player.getUniqueId()));
  }

  @Test
  void remembersNothingWhenDisabled() {
    when(config.getReconnectToLastServer()).thenReturn(0);
    player.rememberServerForReconnect();
    assertEquals(Optional.empty(), tracker.consume(player.getUniqueId()));
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
