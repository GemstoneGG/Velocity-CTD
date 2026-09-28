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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.BackendConnectionPhases;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.protocol.StateRegistry;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ClientForwardFlushTest {

  private ConnectedPlayer player;
  private ClientPlaySessionHandler handler;

  private MinecraftConnection backend() {
    MinecraftConnection smc = mock(MinecraftConnection.class);
    when(smc.isClosed()).thenReturn(false);
    when(smc.getState()).thenReturn(StateRegistry.PLAY);
    VelocityServerConnection serverConnection = mock(VelocityServerConnection.class);
    when(serverConnection.getConnection()).thenReturn(smc);
    when(serverConnection.getPhase()).thenReturn(BackendConnectionPhases.VANILLA);
    when(player.getConnectedServer()).thenReturn(serverConnection);
    return smc;
  }

  @BeforeEach
  void setUp() {
    player = mock(ConnectedPlayer.class);
    when(player.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_4);
    handler = new ClientPlaySessionHandler(mock(VelocityServer.class), player);
  }

  @Test
  void oneFlushPerRead() {
    final MinecraftConnection smc = backend();
    handler.handleUnknown(Unpooled.buffer(4));
    handler.handleUnknown(Unpooled.buffer(4));
    handler.handleUnknown(Unpooled.buffer(4));
    verify(smc, times(3)).delayedWrite(any());
    verify(smc, never()).flush();
    verify(smc, never()).write(any());
    handler.readCompleted();
    verify(smc, times(1)).flush();
  }

  @Test
  void handlerSwitchFlushes() {
    MinecraftConnection smc = backend();
    handler.handleUnknown(Unpooled.buffer(4));
    handler.deactivated();
    verify(smc, times(1)).flush();
  }

  @Test
  void disconnectFlushesBeforeTeardown() {
    MinecraftConnection smc = backend();
    handler.handleUnknown(Unpooled.buffer(4));
    handler.disconnected();
    InOrder order = inOrder(smc, player);
    order.verify(smc).flush();
    order.verify(player).teardown();
  }

  @Test
  void nothingForwardedOutsidePlay() {
    MinecraftConnection smc = backend();
    when(smc.getState()).thenReturn(StateRegistry.CONFIG);
    handler.handleUnknown(Unpooled.buffer(4));
    verify(smc, never()).delayedWrite(any());
  }

  @Test
  void noServerMeansNothingToFlush() {
    when(player.getConnectedServer()).thenReturn(null);
    handler.readCompleted();
    handler.deactivated();
    handler.disconnected();
  }
}
