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

package com.velocitypowered.proxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.util.FallbackServers;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class ForcedHostCustomOptionsTest {

  @Test
  void testForcedHostCustomOptionsParsing(@TempDir Path tempDir) throws IOException {
    Path configFile = tempDir.resolve("velocity.toml");
    String configContent = """
        config-version = "2.9"
        bind = "0.0.0.0:25565"

        [servers]
        lobby = "127.0.0.1:30066"
        lobby-fallback = "127.0.0.1:30067"
        try = ["lobby"]

        [forced-hosts."lobby.example.com"]
        servers = ["lobby"]
        fallback-servers = ["lobby-fallback"]
        motd = ["<red>Custom MOTD</red>"]
        motd-hover = ["<gray>Hover Line</gray>"]

        [forced-hosts."single.example.com"]
        servers = ["lobby"]
        fallback-servers = "lobby-fallback"
        motd = "<blue>Single Line MOTD</blue>"
        motd-hover = "<green>Single Hover</green>"
        """;

    Files.writeString(configFile, configContent);

    VelocityConfiguration config = VelocityConfiguration.read(configFile);

    VelocityConfiguration.ForcedHostEntry entry1 = config.getForcedHostEntries().get("lobby.example.com");
    assertNotNull(entry1);
    assertEquals(List.of("lobby"), entry1.getServers());
    assertEquals(List.of("lobby-fallback"), entry1.getFallbackServers());
    assertEquals(List.of("<red>Custom MOTD</red>"), entry1.getMotd());
    assertEquals(List.of("<gray>Hover Line</gray>"), entry1.getMotdHover());
    assertNull(entry1.getFavicon());

    VelocityConfiguration.ForcedHostEntry entry2 = config.getForcedHostEntries().get("single.example.com");
    assertNotNull(entry2);
    assertEquals(List.of("lobby"), entry2.getServers());
    assertEquals(List.of("lobby-fallback"), entry2.getFallbackServers());
    assertEquals(List.of("<blue>Single Line MOTD</blue>"), entry2.getMotd());
    assertEquals(List.of("<green>Single Hover</green>"), entry2.getMotdHover());
  }

  @Test
  void testFallbackServersResolution(@TempDir Path tempDir) throws IOException {
    Path configFile = tempDir.resolve("velocity.toml");
    String configContent = """
        config-version = "2.9"
        bind = "0.0.0.0:25565"

        [servers]
        lobby = "127.0.0.1:30066"
        lobby-fallback = "127.0.0.1:30067"
        limbo = "127.0.0.1:30068"
        try = ["lobby"]

        [forced-hosts]
        "lobby.example.com" = { servers = ["lobby"], fallback-servers = ["lobby-fallback", "limbo"] }
        """;

    Files.writeString(configFile, configContent);
    VelocityConfiguration config = VelocityConfiguration.read(configFile);

    VelocityServer mockServer = Mockito.mock(VelocityServer.class);
    Mockito.when(mockServer.getConfiguration()).thenReturn(config);

    InboundConnection mockConnection = Mockito.mock(InboundConnection.class);
    Mockito.when(mockConnection.getVirtualHost()).thenReturn(Optional.of(new InetSocketAddress("lobby.example.com", 25565)));

    FallbackServers fallbackServers = FallbackServers.resolveFallbackServers(mockServer, mockConnection);
    assertNotNull(fallbackServers);
    assertEquals(List.of("lobby", "lobby-fallback", "limbo"), fallbackServers.serversToTry());
    assertEquals("lobby.example.com", fallbackServers.virtualHost());
  }
}
