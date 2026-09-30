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

package com.velocitypowered.proxy.connection.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.PingPassthroughMode;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/**
 * Server list pings with passthrough on reach the backend through the shared backend pings.
 */
class ServerListPingPassthroughTest {

  @Test
  void floodOfPingsIsOneBackendPing() {
    final VelocityServer server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    final VelocityConfiguration configuration = server.getConfiguration();
    when(configuration.getPingPassthrough())
        .thenReturn(new PingPassthroughMode(false, false, true, false, false));
    when(configuration.getAttemptConnectionOrder()).thenReturn(List.of("lobby"));
    when(configuration.getForcedHostEntries()).thenReturn(Map.of());
    when(configuration.getMinimumVersion()).thenReturn("1.7.2");
    when(configuration.getMaximumVersion()).thenReturn(Optional.empty());
    when(configuration.getMotdLines()).thenReturn(List.of("proxy"));
    when(configuration.getMotdHoverLines()).thenReturn(List.of());
    when(configuration.getFavicon()).thenReturn(Optional.empty());
    when(configuration.getFallbackVersionPing()).thenReturn("Velocity");

    final VelocityRegisteredServer lobby = mock(VelocityRegisteredServer.class);
    when(lobby.getServerInfo())
        .thenReturn(new ServerInfo("lobby", InetSocketAddress.createUnresolved("lobby", 25565)));
    final ServerPing backendAnswer = new ServerPing(
        new ServerPing.Version(769, "lobby"), null, Component.text("from the lobby"), null);
    when(lobby.ping(any(), any(PingOptions.class)))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(backendAnswer));
    when(server.getServer("lobby")).thenReturn(Optional.of(lobby));

    final VelocityInboundConnection connection =
        mock(VelocityInboundConnection.class, RETURNS_DEEP_STUBS);
    when(connection.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_4);
    when(connection.getVirtualHost())
        .thenReturn(Optional.of(InetSocketAddress.createUnresolved("play.example.com", 25565)));

    final ServerListPingHandler handler = new ServerListPingHandler(server);
    for (int i = 0; i < 500; i++) {
      final ServerPing ping = handler.getInitialPing(connection).join();
      assertEquals(Component.text("from the lobby"), ping.getDescriptionComponent());
    }
    verify(lobby, times(1)).ping(any(), any(PingOptions.class));
  }
}
