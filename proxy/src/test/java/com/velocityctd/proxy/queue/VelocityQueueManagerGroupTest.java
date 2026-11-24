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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.SelectionStrategy;
import com.velocityctd.api.queue.ServerStatus;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the lifecycle of shared queues backing logical server groups.
 */
class VelocityQueueManagerGroupTest {

  private static final String GROUP = "survival";

  private VelocityConfiguration.Queue queueConfig;
  private VelocityServer server;
  private Map<String, VelocityRegisteredServer> registered;
  private AtomicReference<Map<String, VelocityConfiguration.Queue.ServerGroupDefinition>> definitions;
  private AtomicReference<List<String>> noQueueServers;
  private VelocityQueueManager manager;

  @BeforeEach
  void setUp() {
    VelocityQueueManager.getLastTurnedOnlineTime().clear();
    definitions = new AtomicReference<>(Map.of());
    noQueueServers = new AtomicReference<>(List.of());
    queueConfig = mock(VelocityConfiguration.Queue.class);
    when(queueConfig.getGroups()).thenAnswer(invocation -> definitions.get());
    when(queueConfig.getNoQueueServers()).thenAnswer(invocation -> noQueueServers.get());
    when(queueConfig.getBackendPingInterval()).thenReturn(60.0);
    when(queueConfig.getSendDelay()).thenReturn(60.0);
    when(queueConfig.getMessageDelay()).thenReturn(60.0);
    when(queueConfig.getQueueDelay()).thenReturn(0.0);
    when(queueConfig.isEnabled()).thenReturn(true);
    when(queueConfig.getQueueServer()).thenReturn("");
    when(queueConfig.isAllowPausedQueueJoining()).thenReturn(false);
    when(queueConfig.isAllowMultiQueue()).thenReturn(false);

    VelocityConfiguration config = mock(VelocityConfiguration.class);
    when(config.getQueue()).thenReturn(queueConfig);

    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    when(server.getConfiguration()).thenReturn(config);
    when(server.getRedis()).thenReturn(null);
    when(server.getServer(any(String.class))).thenReturn(Optional.empty());
    registered = new java.util.concurrent.ConcurrentHashMap<>();
    when(server.getAllServers()).thenAnswer(invocation -> List.copyOf(registered.values()));
  }

  private static VelocityConfiguration.Queue.ServerGroupDefinition definition(
      String name, List<String> members) {
    return new VelocityConfiguration.Queue.ServerGroupDefinition(
        name, members, SelectionStrategy.LEAST_PLAYERS, -1);
  }

  private VelocityRegisteredServer register(String name) {
    VelocityRegisteredServer rs = mock(VelocityRegisteredServer.class);
    when(rs.getServerInfo()).thenReturn(new ServerInfo(name, new InetSocketAddress("127.0.0.1", 1)));
    when(rs.getPlayersConnected()).thenReturn(List.of());
    registered.put(name, rs);
    when(server.getServer(name)).thenReturn(Optional.of(rs));
    return rs;
  }

  private void group(String name, List<String> members) {
    definitions.set(Map.of(name, definition(name, members)));
  }

  private VelocityQueueManager newManager() {
    manager = new VelocityQueueManager(server);
    return manager;
  }

  private boolean hasQueue(String name) {
    return manager.getQueues().stream().anyMatch(queue -> queue.getName().equals(name));
  }

  @Test
  void reloadAppliesChangedMembersThroughNameCapturingResolver() {
    VelocityRegisteredServer first = register("Survival-01");
    VelocityRegisteredServer second = register("Survival-02");
    VelocityRegisteredServer replacement = register("Survival-03");
    group(GROUP, List.of("Survival-01", "Survival-02"));
    newManager();

    VelocityQueue<?> groupQueue = manager.getQueue(GROUP);
    assertTrue(groupQueue.isGroupQueue());
    assertEquals("survival", groupQueue.getName());
    UUID playerId = UUID.randomUUID();
    assertTrue(groupQueue.resolveTargetServer(playerId).isEmpty(),
        "No configured member is online yet");

    group(GROUP, List.of("Survival-01", "Survival-03"));
    manager.getQueue("Survival-03").setServerStatus(ServerStatus.ONLINE);
    manager.reload();

    assertSame(groupQueue, manager.getQueue(GROUP), "A reload must reuse the existing queue");
    assertEquals(Optional.of(replacement), groupQueue.resolveTargetServer(playerId));
    assertTrue(groupQueue.resolveTargetServer(playerId).isPresent());
    assertTrue(first != replacement && second != replacement);
  }

  @Test
  void pauseSurvivesReloadAndRepeatedHealthRefresh() {
    register("Survival-01");
    group(GROUP, List.of("Survival-01"));
    newManager();
    VelocityQueue<?> groupQueue = manager.getQueue(GROUP);
    groupQueue.setState(QueueState.PAUSED);

    for (int cycle = 0; cycle < 4; cycle++) {
      group(GROUP, List.of("Survival-01", "Survival-02"));
      manager.getServerGroupManager().refresh();
      manager.reload();
      assertEquals(QueueState.PAUSED, groupQueue.getState());
      assertSame(groupQueue, manager.getQueue(GROUP));
    }
  }

  @Test
  void groupQueueIsRemovedWhenGroupIsDeleted() {
    register("Survival-01");
    group(GROUP, List.of("Survival-01"));
    newManager();
    assertTrue(hasQueue(GROUP));

    definitions.set(Map.of());
    manager.reload();

    assertFalse(hasQueue(GROUP));
    assertThrows(IllegalArgumentException.class, () -> manager.getQueue(GROUP));
  }

  @Test
  void groupQueueIsRemovedWhenAllMembersAreUnregistered() {
    VelocityRegisteredServer backend = register("Survival-01");
    group(GROUP, List.of("Survival-01"));
    newManager();
    assertTrue(hasQueue(GROUP));

    registered.remove("Survival-01");
    when(server.getServer("Survival-01")).thenReturn(Optional.empty());
    manager.reload();

    assertFalse(hasQueue(GROUP));
    assertTrue(backend != null);
  }

  @Test
  void noGroupQueueIsCreatedForUnknownMembers() {
    group(GROUP, List.of("Ghost-01"));
    newManager();

    assertTrue(manager.getServerGroupManager().hasGroup(GROUP));
    assertFalse(hasQueue(GROUP));
    assertThrows(IllegalArgumentException.class, () -> manager.getQueue(GROUP));
  }

  @Test
  void ordinaryPhysicalQueuesKeepTheirNamesAndStates() {
    register("Survival-01");
    register("Lobby-01");
    noQueueServers.set(List.of("Lobby-01"));
    group(GROUP, List.of("Survival-01"));
    newManager();

    VelocityQueue<?> physical = manager.getQueue("Survival-01");
    assertEquals("Survival-01", physical.getName());
    assertFalse(physical.isGroupQueue());
    assertEquals(QueueState.ACTIVE, physical.getState());
    assertEquals(QueueState.INACTIVE, manager.getQueue("Lobby-01").getState());
    assertTrue(manager.getQueue(GROUP).isGroupQueue());
  }

  @Test
  void disabledQueueJoiningConnectsDirectlyToGroupBackend() {
    VelocityRegisteredServer backend = register("Survival-01");
    group(GROUP, List.of("Survival-01"));
    newManager();
    manager.getQueue("Survival-01").setServerStatus(ServerStatus.ONLINE);
    when(queueConfig.isEnabled()).thenReturn(false);

    ConnectedPlayer player = mock(ConnectedPlayer.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.hasPermission(any(String.class))).thenReturn(false);
    ConnectionRequestBuilder request = mock(ConnectionRequestBuilder.class);
    when(player.createConnectionRequest(backend)).thenReturn(request);
    when(request.connectWithIndication()).thenReturn(CompletableFuture.completedFuture(true));

    manager.queue(player, GROUP);

    verify(player).createConnectionRequest(backend);
    assertEquals(0, manager.getQueue(GROUP).size());
  }
}
