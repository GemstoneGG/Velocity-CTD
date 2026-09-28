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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;

class TransitionSessionHandlerTest {

  private final MinecraftConnection playerConnection = mock(MinecraftConnection.class);
  private final TransitionSessionHandler handler = handler();

  @Test
  void packetsBeforeTheSwitchCompletesAreReplayedAfterJoinGameInOrder() throws Exception {
    final MinecraftPacket decoded = mock(MinecraftPacket.class);
    final ByteBuf raw = Unpooled.buffer().writeInt(1873);

    handler.handleGeneric(decoded);
    handler.handleUnknown(raw);

    assertEquals(2, raw.refCnt(), "an undecoded packet is held, not dropped");
    verify(playerConnection, never()).delayedWrite(ArgumentMatchers.any());

    completeSwitch();

    InOrder order = inOrder(playerConnection);
    order.verify(playerConnection).delayedWrite(decoded);
    order.verify(playerConnection).delayedWrite(raw);
    order.verify(playerConnection).flush();
    raw.release(raw.refCnt());
  }

  @Test
  void heldPacketsAreReleasedWhenTheBackendDisconnects() {
    final ByteBuf raw = Unpooled.buffer().writeInt(1873);
    handler.handleUnknown(raw);
    assertEquals(2, raw.refCnt());

    handler.disconnected();

    assertEquals(1, raw.refCnt());
    raw.release();
  }

  private TransitionSessionHandler handler() {
    ConnectedPlayer player = mock(ConnectedPlayer.class);
    when(player.getConnection()).thenReturn(playerConnection);
    VelocityServerConnection serverConn = mock(VelocityServerConnection.class);
    when(serverConn.getPlayer()).thenReturn(player);
    return new TransitionSessionHandler(mock(VelocityServer.class), serverConn,
        new CompletableFuture<>());
  }

  private void completeSwitch() throws Exception {
    Method flush = TransitionSessionHandler.class.getDeclaredMethod("flushDeferredPackets");
    flush.setAccessible(true);
    flush.invoke(handler);
  }
}
