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
import static org.mockito.Mockito.when;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class BackendPlaySessionHandlerTest {

  private static final ProtocolVersion V1_8 = ProtocolVersion.MINECRAFT_1_8;

  @Test
  void footerTheProxyCannotParseIsForwardedAsIs() {
    ConnectedPlayer player = mock(ConnectedPlayer.class);
    MinecraftConnection playerConnection = mock(MinecraftConnection.class);
    when(player.getConnection()).thenReturn(playerConnection);
    when(playerConnection.getActiveSessionHandler())
        .thenReturn(mock(ClientPlaySessionHandler.class));
    VelocityServerConnection serverConn = mock(VelocityServerConnection.class);
    when(serverConn.getPlayer()).thenReturn(player);
    BackendPlaySessionHandler handler =
        new BackendPlaySessionHandler(mock(VelocityServer.class), serverConn);

    // A 1.8 client opens this link, but Adventure rejects the newline in it.
    String header = "{\"text\":\"Header\"}";
    String footer = "{\"text\":\"example.com\",\"clickEvent\":{\"action\":\"open_url\","
        + "\"value\":\"https://example.com\\n\"}}";
    HeaderAndFooterPacket packet = new HeaderAndFooterPacket(
        new ComponentHolder(V1_8, header), new ComponentHolder(V1_8, footer));

    assertFalse(handler.handle(packet));

    ByteBuf buf = Unpooled.buffer();
    packet.encode(buf, ProtocolUtils.Direction.CLIENTBOUND, V1_8);
    assertEquals(header, ProtocolUtils.readString(buf));
    assertEquals(footer, ProtocolUtils.readString(buf));
    buf.release();
  }
}
