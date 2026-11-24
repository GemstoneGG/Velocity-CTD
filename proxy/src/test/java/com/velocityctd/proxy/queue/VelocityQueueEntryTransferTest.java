/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.ServerStatus;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for queue transfer failure handling and retry accounting.
 */
class VelocityQueueEntryTransferTest {

  private static final UUID PLAYER_ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
  private static final String GROUP = "survival";

  private VelocityServer server;
  private VelocityConfiguration.Queue queueConfig;
  private ConnectedPlayer player;
  private VelocityRegisteredServer backend;
  private ConnectionRequestBuilder request;
  private VelocityQueueManager queueManager;
  private AtomicInteger resolverCalls;
  private BiFunction<UUID, Boolean, Optional<VelocityRegisteredServer>> resolver;

  @BeforeEach
  void setUp() {
    queueConfig = mock(VelocityConfiguration.Queue.class);
    when(queueConfig.getBannedReason()).thenReturn(List.of());
    when(queueConfig.getMaxSendRetries()).thenReturn(10);

    VelocityConfiguration config = mock(VelocityConfiguration.class);
    when(config.getQueue()).thenReturn(queueConfig);

    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    when(server.getConfiguration()).thenReturn(config);
    player = mock(ConnectedPlayer.class);
    when(player.getUniqueId()).thenReturn(PLAYER_ID);
    when(player.getQueuePriority(anyString())).thenReturn(5);
    when(player.hasPermission(anyString())).thenReturn(false);
    when(server.getPlayer(PLAYER_ID)).thenReturn(Optional.of(player));

    backend = mock(VelocityRegisteredServer.class);
    when(backend.getServerInfo()).thenReturn(
        new ServerInfo("Survival-01", new InetSocketAddress("127.0.0.1", 1)));
    when(backend.ping()).thenReturn(CompletableFuture.completedFuture(mock(ServerPing.class)));

    request = mock(ConnectionRequestBuilder.class);
    when(player.createConnectionRequest(backend)).thenReturn(request);

    queueManager = mock(VelocityQueueManager.class);
    when(queueManager.isMasterProxy()).thenReturn(true);
    resolverCalls = new AtomicInteger();
    resolver = (id, fullBypass) -> {
      resolverCalls.incrementAndGet();
      return Optional.of(backend);
    };
  }

  private LocalVelocityQueue queue() {
    return new LocalVelocityQueue(server, queueManager, backend, QueueState.ACTIVE, GROUP, resolver);
  }

  private VelocityQueueEntry entry(LocalVelocityQueue queue, boolean fullBypass) {
    queue.enqueue(new QueueEntryData(PLAYER_ID, "player", 7, fullBypass, false));
    return queue.getEntry(PLAYER_ID);
  }

  private void connectWith(ConnectionRequestBuilder.Result outcome) {
    when(request.connect()).thenReturn(CompletableFuture.completedFuture(outcome));
  }

  private static ConnectionRequestBuilder.Result result(boolean successful) {
    ConnectionRequestBuilder.Result result = mock(ConnectionRequestBuilder.Result.class);
    when(result.isSuccessful()).thenReturn(successful);
    when(result.getStatus()).thenReturn(successful
        ? ConnectionRequestBuilder.Status.SUCCESS
        : ConnectionRequestBuilder.Status.SERVER_DISCONNECTED);
    when(result.getReasonComponent()).thenReturn(Optional.empty());
    return result;
  }

  @Test
  void noBackendKeepsQueueMembershipAndClearsWaitingWithoutAttempt() {
    resolver = (id, fullBypass) -> {
      resolverCalls.incrementAndGet();
      return Optional.empty();
    };
    LocalVelocityQueue queue = queue();
    VelocityQueueEntry queued = entry(queue, false);
    int position = queued.getPosition();

    queued.transfer();

    assertTrue(queue.contains(PLAYER_ID));
    assertEquals(position, queued.getPosition());
    assertEquals(7, queued.getPriority());
    assertEquals(0, queued.getConnectionAttempts());
    assertFalse(queued.isWaitingForConnection());
    assertEquals(1, resolverCalls.get());
  }

  @Test
  void failedConnectionDiagnosesTheBackendThatWasActuallyAttempted() {
    when(player.getQueuePriority(anyString())).thenReturn(7);
    connectWith(result(false));
    LocalVelocityQueue queue = queue();
    VelocityQueueEntry queued = entry(queue, true);

    queued.transfer();

    assertEquals(1, resolverCalls.get(), "Diagnosis must not re-resolve the backend");
    verify(player).handleServerDisconnectResult(eq(backend), any());
    assertEquals(1, queued.getConnectionAttempts());
    assertFalse(queued.isWaitingForConnection());
    assertTrue(queue.contains(PLAYER_ID));
    assertEquals(7, queued.getPriority());
  }

  @Test
  void fullBypassFlagReachesTheBackendResolver() {
    connectWith(result(false));
    resolver = (id, fullBypass) -> {
      assertTrue(fullBypass, "The entry's bypass flag must be forwarded");
      resolverCalls.incrementAndGet();
      return Optional.of(backend);
    };
    VelocityQueueEntry queued = entry(queue(), true);

    queued.transfer();

    assertEquals(1, resolverCalls.get());
  }

  @Test
  void reachingTheRetryLimitDequeuesThePlayer() {
    when(queueConfig.getMaxSendRetries()).thenReturn(1);
    connectWith(result(false));
    LocalVelocityQueue queue = queue();
    VelocityQueueEntry queued = entry(queue, false);

    queued.transfer();

    assertEquals(1, queued.getConnectionAttempts());
    assertFalse(queue.contains(PLAYER_ID));
  }

  @Test
  void offlinePlayerDoesNotCountFailedAttempt() {
    when(server.getPlayer(PLAYER_ID)).thenReturn(Optional.empty());
    LocalVelocityQueue queue = queue();
    VelocityQueueEntry queued = entry(queue, false);

    queued.transfer();

    assertEquals(0, queued.getConnectionAttempts());
    assertFalse(queued.isWaitingForConnection());
    assertTrue(queue.contains(PLAYER_ID));
  }

  @Test
  void offlineBackendDuringDiagnosisKeepsThePlayerQueuedWithoutPenalty() {
    connectWith(result(false));
    CompletableFuture<ServerPing> failedPing = new CompletableFuture<>();
    failedPing.completeExceptionally(new IllegalStateException("offline"));
    when(backend.ping()).thenReturn(failedPing);
    LocalVelocityQueue queue = queue();
    VelocityQueueEntry queued = entry(queue, false);

    queued.transfer();

    assertEquals(ServerStatus.OFFLINE, queue.getServerStatus());
    assertEquals(0, queued.getConnectionAttempts());
    assertTrue(queue.contains(PLAYER_ID));
    assertFalse(queued.isWaitingForConnection());
  }

  @Test
  void successfulConnectionDequeuesThePlayer() {
    connectWith(result(true));
    LocalVelocityQueue queue = queue();
    VelocityQueueEntry queued = entry(queue, false);

    queued.transfer();

    assertFalse(queue.contains(PLAYER_ID));
  }

  @Test
  void bannedDisconnectReasonRemovesThePlayerWithoutCountingAnAttempt() {
    ConnectionRequestBuilder.Result refused = result(false);
    Component reason = Component.text("you are banned: nope");
    when(refused.getReasonComponent()).thenReturn(Optional.of(reason));
    when(queueConfig.getBannedReason()).thenReturn(List.of("banned"));
    when(request.connect()).thenReturn(CompletableFuture.completedFuture(refused));
    LocalVelocityQueue queue = queue();
    VelocityQueueEntry queued = entry(queue, false);

    queued.transfer();

    assertFalse(queue.contains(PLAYER_ID));
    assertEquals(0, queued.getConnectionAttempts());
  }

  @Test
  void connectionExceptionCountsFailedAttempt() {
    CompletableFuture<ConnectionRequestBuilder.Result> failed = new CompletableFuture<>();
    failed.completeExceptionally(new IllegalStateException("boom"));
    when(request.connect()).thenReturn(failed);
    LocalVelocityQueue queue = queue();
    VelocityQueueEntry queued = entry(queue, false);

    queued.transfer();

    assertEquals(1, queued.getConnectionAttempts());
    assertTrue(queue.contains(PLAYER_ID));
  }

  @Test
  void staleWaitingFlagCanBeResetForRetry() {
    LocalVelocityQueue queue = queue();
    VelocityQueueEntry queued = entry(queue, false);
    java.lang.reflect.Field field;
    try {
      field = VelocityQueueEntry.class.getDeclaredField("waitingForConnection");
      field.setAccessible(true);
      field.setBoolean(queued, true);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
    assertTrue(queued.isWaitingForConnection());

    queued.resetWaitingForRetry();

    assertFalse(queued.isWaitingForConnection());
    assertEquals(0, queued.getConnectionAttempts());
    assertTrue(queue.contains(PLAYER_ID));
  }
}
