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

package com.velocitypowered.proxy.protocol.packet.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatQueueTest {

  private EmbeddedChannel backendChannel;
  private List<MinecraftPacket> written;
  private ConnectedPlayer player;
  private ChatQueue queue;

  @BeforeEach
  void setUp() {
    backendChannel = new EmbeddedChannel();
    written = new ArrayList<>();

    // A backend that never finishes a write, like one that stopped reading its socket
    MinecraftConnection smc = mock(MinecraftConnection.class);
    when(smc.eventLoop()).thenReturn(backendChannel.eventLoop());
    when(smc.write(any())).thenAnswer(invocation -> {
      written.add(invocation.getArgument(0));
      return backendChannel.newPromise();
    });

    VelocityServerConnection serverConnection = mock(VelocityServerConnection.class);
    when(serverConnection.getConnection()).thenReturn(smc);
    player = mock(ConnectedPlayer.class);
    when(player.getCurrentServer()).thenReturn(Optional.of(serverConnection));

    queue = new ChatQueue(player);
  }

  @AfterEach
  void tearDown() {
    backendChannel.finishAndReleaseAll();
  }

  @Test
  void unfinishedWriteDoesNotHoldUpNextPacket() {
    MinecraftPacket first = new ChatAcknowledgementPacket(1);
    MinecraftPacket second = new ChatAcknowledgementPacket(2);

    queue.queuePacket(chatState -> first);
    queue.queuePacket(chatState -> second);
    backendChannel.runPendingTasks();

    assertEquals(List.of(first, second), written);
  }

  @Test
  void pendingPacketStillHoldsUpLaterPackets() {
    CompletableFuture<MinecraftPacket> commandEvent = new CompletableFuture<>();
    MinecraftPacket second = new ChatAcknowledgementPacket(2);

    queue.queuePacket(lastSeenMessages -> commandEvent, null, null);
    queue.queuePacket(chatState -> second);
    backendChannel.runPendingTasks();

    assertTrue(written.isEmpty());

    MinecraftPacket first = new ChatAcknowledgementPacket(1);
    commandEvent.complete(first);
    backendChannel.runPendingTasks();

    assertEquals(List.of(first, second), written);
  }

  @Test
  void packetsWaitingBehindPendingOneHitTheCap() {
    queue.queuePacket(lastSeenMessages -> new CompletableFuture<>(), null, null);
    for (int i = 1; i < 256; i++) {
      queue.queuePacket(chatState -> new ChatAcknowledgementPacket(1));
    }
    verify(player, never()).disconnect(any());

    queue.queuePacket(chatState -> new ChatAcknowledgementPacket(1));
    verify(player, times(1)).disconnect(any());

    queue.queuePacket(chatState -> new ChatAcknowledgementPacket(1));
    verify(player, times(1)).disconnect(any());
  }

  @Test
  void finishedPacketsFreeTheirRoom() {
    for (int i = 0; i < 1000; i++) {
      queue.queuePacket(chatState -> new ChatAcknowledgementPacket(1));
      backendChannel.runPendingTasks();
    }

    verify(player, never()).disconnect(any());
    assertEquals(1000, written.size());
  }

  @Test
  void packetsTheProxySendsDoNotCountTowardTheCap() {
    queue.queuePacket(lastSeenMessages -> new CompletableFuture<>(), null, null);
    for (int i = 0; i < 1000; i++) {
      queue.queueProxyPacket(chatState -> new ChatAcknowledgementPacket(1));
    }

    verify(player, never()).disconnect(any());
  }
}
