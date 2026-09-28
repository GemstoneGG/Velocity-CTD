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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ConfigSessionHandlerTest {

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
}
