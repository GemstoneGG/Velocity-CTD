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

package com.velocityctd.proxy.queue.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocityctd.api.queue.LastServerStore;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.SelectionStrategy;
import com.velocityctd.api.queue.ServerStatus;
import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocityctd.proxy.queue.VelocityQueueManager;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for logical group health, selection and last-server recording.
 */
class ServerGroupManagerTest {

  private static final String GROUP = "survival";
  private static final List<String> MEMBERS = List.of("Survival-01", "Survival-02", "Survival-03");

  private VelocityConfiguration.Queue queueConfig;
  private VelocityServer server;
  private VelocityQueueManager queueManager;
  private Map<String, VelocityRegisteredServer> registered;
  private Map<String, AtomicReference<ServerStatus>> statuses;
  private AtomicReference<Map<String, VelocityConfiguration.Queue.ServerGroupDefinition>> definitions;

  @BeforeEach
  void setUp() {
    VelocityQueueManager.getLastTurnedOnlineTime().clear();
    definitions = new AtomicReference<>(Map.of());
    queueConfig = mock(VelocityConfiguration.Queue.class);
    when(queueConfig.getGroups()).thenAnswer(invocation -> definitions.get());
    when(queueConfig.getBackendPingInterval()).thenReturn(5.0);
    when(queueConfig.getQueueDelay()).thenReturn(0.0);

    VelocityConfiguration config = mock(VelocityConfiguration.class);
    when(config.getQueue()).thenReturn(queueConfig);

    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    when(server.getConfiguration()).thenReturn(config);
    when(server.getRedis()).thenReturn(null);
    when(server.getServer(anyString())).thenReturn(Optional.empty());

    queueManager = mock(VelocityQueueManager.class);
    when(queueManager.isMasterProxy()).thenReturn(true);
    registered = new java.util.HashMap<>();
    statuses = new java.util.HashMap<>();
    when(server.getAllServers()).thenAnswer(invocation -> List.copyOf(registered.values()));
  }

  private static VelocityConfiguration.Queue.ServerGroupDefinition definition(
      String name, List<String> members, SelectionStrategy strategy, int maxPlayers) {
    return new VelocityConfiguration.Queue.ServerGroupDefinition(name, members, strategy, maxPlayers);
  }

  private void configure(String name, List<String> members, SelectionStrategy strategy, int maxPlayers) {
    definitions.set(Map.of(name, definition(name, members, strategy, maxPlayers)));
  }

  private void register(String name, int players) {
    VelocityRegisteredServer rs = mock(VelocityRegisteredServer.class);
    when(rs.getServerInfo()).thenReturn(new ServerInfo(name, new InetSocketAddress("127.0.0.1", 1)));
    when(rs.getPlayersConnected()).thenReturn(java.util.Collections.nCopies(players, mock(
        com.velocitypowered.proxy.connection.client.ConnectedPlayer.class)));
    registered.put(name, rs);
    when(server.getServer(name)).thenReturn(Optional.of(rs));
  }

  private VelocityQueue<?> track(String name, ServerStatus status) {
    AtomicReference<ServerStatus> reference = new AtomicReference<>(status);
    statuses.put(name, reference);
    VelocityQueue<?> queue = mock(VelocityQueue.class);
    when(queue.getServerStatus()).thenAnswer(invocation -> reference.get());
    doAnswer(invocation -> {
      reference.set(invocation.getArgument(0));
      return null;
    }).when(queue).setServerStatus(any(ServerStatus.class));
    doReturn(queue).when(queueManager).getQueue(name);
    return queue;
  }

  private ServerGroupManager newManager() {
    return new ServerGroupManager(server, queueManager);
  }

  private static Object field(Object target, String name) throws Exception {
    Field found = target.getClass().getDeclaredField(name);
    found.setAccessible(true);
    return found.get(target);
  }

  private static void callConnect(ServerGroupManager manager, UUID playerId, String backend)
      throws Exception {
    Method method = ServerGroupManager.class.getDeclaredMethod(
        "recordConnection", UUID.class, String.class);
    method.setAccessible(true);
    method.invoke(manager, playerId, backend);
  }

  @Test
  void refreshSetsGroupOnlineWhenAnyMemberIsActive() {
    configure(GROUP, MEMBERS, SelectionStrategy.LEAST_PLAYERS, -1);
    for (String member : MEMBERS) {
      register(member, 0);
    }
    for (String member : MEMBERS) {
      track(member, ServerStatus.OFFLINE);
    }
    VelocityQueue<?> groupQueue = track(GROUP, ServerStatus.OFFLINE);
    statuses.get("Survival-02").set(ServerStatus.ONLINE);
    ServerGroupManager manager = newManager();

    manager.refresh();

    assertEquals(ServerStatus.ONLINE, statuses.get(GROUP).get());
    verify(groupQueue, never()).setState(any(QueueState.class));
  }

  @Test
  void refreshSetsGroupOfflineWhenNoMemberIsActive() {
    configure(GROUP, MEMBERS, SelectionStrategy.LEAST_PLAYERS, -1);
    for (String member : MEMBERS) {
      register(member, 0);
    }
    for (String member : MEMBERS) {
      track(member, ServerStatus.OFFLINE);
    }
    track(GROUP, ServerStatus.ONLINE);
    ServerGroupManager manager = newManager();

    manager.refresh();

    assertEquals(ServerStatus.OFFLINE, statuses.get(GROUP).get());
  }

  @Test
  void refreshWaitsForWarmupThenPromotesGroup() {
    configure(GROUP, MEMBERS, SelectionStrategy.LEAST_PLAYERS, -1);
    when(queueConfig.getQueueDelay()).thenReturn(0.0);
    for (String member : MEMBERS) {
      register(member, 0);
    }
    for (String member : MEMBERS) {
      track(member, ServerStatus.OFFLINE);
    }
    statuses.get("Survival-01").set(ServerStatus.ONLINE);
    track(GROUP, ServerStatus.OFFLINE);
    ServerGroupManager manager = newManager();

    manager.refresh();
    assertEquals(ServerStatus.ONLINE, statuses.get(GROUP).get());

    // A fresh transition from OFFLINE starts the warmup window.
    statuses.get(GROUP).set(ServerStatus.OFFLINE);
    when(queueConfig.getQueueDelay()).thenReturn(5.0);
    statuses.get("Survival-01").set(ServerStatus.ONLINE);
    manager.refresh();
    assertEquals(ServerStatus.WAITING, statuses.get(GROUP).get());

    VelocityQueueManager.getLastTurnedOnlineTime().put(
        GROUP, System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(10));
    manager.refresh();
    assertEquals(ServerStatus.ONLINE, statuses.get(GROUP).get());
  }

  @Test
  void refreshReportsFullWhenEveryActiveMemberIsAtCapacity() {
    configure(GROUP, MEMBERS, SelectionStrategy.LEAST_PLAYERS, -1);
    for (String member : MEMBERS) {
      register(member, 10);
    }
    for (String member : MEMBERS) {
      track(member, ServerStatus.FULL);
    }
    track(GROUP, ServerStatus.ONLINE);
    ServerGroupManager manager = newManager();

    manager.refresh();

    assertEquals(ServerStatus.FULL, statuses.get(GROUP).get());
  }

  @Test
  void refreshLeavesAdministrativePauseUntouched() {
    configure(GROUP, MEMBERS, SelectionStrategy.LEAST_PLAYERS, -1);
    for (String member : MEMBERS) {
      register(member, 0);
    }
    for (String member : MEMBERS) {
      track(member, ServerStatus.ONLINE);
    }
    VelocityQueue<?> groupQueue = track(GROUP, ServerStatus.ONLINE);
    when(groupQueue.getState()).thenReturn(QueueState.PAUSED);
    ServerGroupManager manager = newManager();

    for (int index = 0; index < 5; index++) {
      statuses.get("Survival-01").set(index % 2 == 0 ? ServerStatus.OFFLINE : ServerStatus.ONLINE);
      manager.refresh();
    }

    verify(groupQueue, never()).setState(any(QueueState.class));
  }

  @Test
  void nonMasterProxyDoesNotComputeGroupStatus() {
    configure(GROUP, MEMBERS, SelectionStrategy.LEAST_PLAYERS, -1);
    for (String member : MEMBERS) {
      register(member, 0);
    }
    for (String member : MEMBERS) {
      track(member, ServerStatus.ONLINE);
    }
    VelocityQueue<?> groupQueue = track(GROUP, ServerStatus.OFFLINE);
    when(queueManager.isMasterProxy()).thenReturn(false);
    ServerGroupManager manager = newManager();

    manager.refresh();

    verify(groupQueue, never()).setServerStatus(any(ServerStatus.class));
    assertEquals(ServerStatus.OFFLINE, statuses.get(GROUP).get());
  }

  @Test
  void selectionUsesLeastPlayersAcrossConfiguredMembers() {
    configure(GROUP, MEMBERS, SelectionStrategy.LEAST_PLAYERS, -1);
    register("Survival-01", 12);
    register("Survival-02", 4);
    register("Survival-03", 9);
    for (String member : MEMBERS) {
      track(member, ServerStatus.ONLINE);
    }
    ServerGroupManager manager = newManager();

    assertEquals(Optional.of("Survival-02"),
        manager.selectBackend(GROUP, UUID.randomUUID()));
  }

  @Test
  void selectionHonoursLastServerHintWhenMemberIsAvailable() throws Exception {
    configure(GROUP, MEMBERS, SelectionStrategy.LAST_SERVER, -1);
    for (String member : MEMBERS) {
      register(member, 8);
    }
    for (String member : MEMBERS) {
      track(member, ServerStatus.ONLINE);
    }
    ServerGroupManager manager = newManager();
    UUID playerId = UUID.randomUUID();
    LastServerStore store = (LastServerStore) field(manager, "lastServerStore");
    store.recordLastServer(playerId, GROUP, "Survival-03");

    assertEquals(Optional.of("Survival-03"), manager.selectBackend(GROUP, playerId));
  }

  @Test
  void fullBypassAllowsSelectingCapacityLimitedBackend() {
    configure(GROUP, List.of("Survival-01"), SelectionStrategy.LEAST_PLAYERS, 1);
    register("Survival-01", 5);
    track("Survival-01", ServerStatus.FULL);
    ServerGroupManager manager = newManager();
    UUID playerId = UUID.randomUUID();

    assertTrue(manager.selectBackend(GROUP, playerId).isEmpty());
    assertEquals(Optional.of("Survival-01"),
        manager.selectBackend(GROUP, playerId, true));
  }

  @Test
  void selectionNeverWritesLastServerSpeculatively() throws Exception {
    configure(GROUP, MEMBERS, SelectionStrategy.ROUND_ROBIN, -1);
    for (String member : MEMBERS) {
      register(member, 0);
    }
    for (String member : MEMBERS) {
      track(member, ServerStatus.ONLINE);
    }
    ServerGroupManager manager = newManager();
    LastServerStore store = mock(LastServerStore.class);
    when(store.getLastServer(any(UUID.class), anyString())).thenReturn(Optional.empty());
    Field found = ServerGroupManager.class.getDeclaredField("lastServerStore");
    found.setAccessible(true);
    found.set(manager, store);

    manager.selectBackend(GROUP, UUID.randomUUID());

    verify(store).getLastServer(any(UUID.class), anyString());
    verify(store, never()).recordLastServer(any(UUID.class), anyString(), anyString());
  }

  @Test
  void connectionRecordsLastServerForEveryGroupContainingTheBackend() throws Exception {
    definitions.set(Map.of(
        "alpha", definition("alpha", List.of("Survival-01", "Lobby"),
            SelectionStrategy.LEAST_PLAYERS, -1),
        "beta", definition("beta", List.of("Survival-01", "Creative"),
            SelectionStrategy.LEAST_PLAYERS, -1),
        "gamma", definition("gamma", List.of("Lobby"), SelectionStrategy.LEAST_PLAYERS, -1)));
    ServerGroupManager manager = newManager();
    LastServerStore store = mock(LastServerStore.class);
    Field found = ServerGroupManager.class.getDeclaredField("lastServerStore");
    found.setAccessible(true);
    found.set(manager, store);
    UUID playerId = UUID.randomUUID();

    callConnect(manager, playerId, "Survival-01");
    callConnect(manager, playerId, "Lobby");
    callConnect(manager, playerId, "");

    verify(store).recordLastServer(playerId, "alpha", "Survival-01");
    verify(store).recordLastServer(playerId, "beta", "Survival-01");
    verify(store).recordLastServer(playerId, "alpha", "Lobby");
    verify(store).recordLastServer(playerId, "gamma", "Lobby");
    verify(store, never()).recordLastServer(playerId, "beta", "Lobby");
    verify(store, never()).recordLastServer(playerId, "gamma", "Survival-01");
  }

  @Test
  void groupsDoNotShareLastServerHistory() throws Exception {
    definitions.set(Map.of(
        "alpha", definition("alpha", List.of("Survival-01"),
            SelectionStrategy.LEAST_PLAYERS, -1),
        "beta", definition("beta", List.of("Survival-02"),
            SelectionStrategy.LEAST_PLAYERS, -1)));
    ServerGroupManager manager = newManager();
    Object store = field(manager, "lastServerStore");
    UUID playerId = UUID.randomUUID();
    Method record = LastServerStore.class.getMethod(
        "recordLastServer", UUID.class, String.class, String.class);
    record.invoke(store, playerId, "alpha", "Survival-01");

    assertEquals(Optional.of("Survival-01"),
        ((LastServerStore) store).getLastServer(playerId, "alpha"));
    assertTrue(((LastServerStore) store).getLastServer(playerId, "beta").isEmpty());
    assertTrue(((LastServerStore) store).getLastServer(playerId).isEmpty());
  }

  @Test
  void rebuildRejectsGroupNameCollidingWithPhysicalServer() {
    register("lobby", 0);
    configure("lobby", List.of("Survival-01"), SelectionStrategy.LEAST_PLAYERS, -1);
    ServerGroupManager manager = newManager();

    assertFalse(manager.hasGroup("lobby"));
    assertTrue(manager.getGroups().isEmpty());
  }

  @Test
  void rebuildDeduplicatesGroupNamesIgnoringCase() {
    Map<String, VelocityConfiguration.Queue.ServerGroupDefinition> raw = new java.util.LinkedHashMap<>();
    raw.put("lobby", definition("lobby", List.of("Survival-01"),
        SelectionStrategy.LEAST_PLAYERS, -1));
    raw.put("LOBBY", definition("LOBBY", List.of("Survival-02"),
        SelectionStrategy.LEAST_PLAYERS, -1));
    definitions.set(raw);
    ServerGroupManager manager = newManager();

    Collection<VelocityServerGroup> groups = manager.getGroups();
    assertEquals(1, groups.size());
    assertTrue(groups.iterator().next().getServers().contains("Survival-01"));
  }

  @Test
  void roundRobinSelectionRemainsThreadSafeAndWraps() throws Exception {
    configure(GROUP, MEMBERS, SelectionStrategy.ROUND_ROBIN, -1);
    for (String member : MEMBERS) {
      register(member, 0);
    }
    for (String member : MEMBERS) {
      track(member, ServerStatus.ONLINE);
    }
    ServerGroupManager manager = newManager();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    ConcurrentLinkedQueue<String> chosen = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
    List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
    UUID playerId = UUID.randomUUID();

    try {
      for (int index = 0; index < 120; index++) {
        futures.add(pool.submit(() -> {
          try {
            start.await();
            manager.selectBackend(GROUP, playerId).ifPresent(chosen::add);
          } catch (Throwable throwable) {
            failures.add(throwable);
          }
        }));
      }
      start.countDown();
      for (java.util.concurrent.Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertTrue(failures.isEmpty(), "Concurrent selection must not fail");
    assertEquals(120, chosen.size());
    assertEquals(Set.copyOf(MEMBERS), Set.copyOf(chosen));
    for (String member : MEMBERS) {
      assertTrue(chosen.stream().filter(member::equals).count() >= 30,
          "Every backend should be chosen repeatedly: " + member);
    }
  }
}
