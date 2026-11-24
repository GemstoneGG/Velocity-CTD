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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.SelectionStrategy;
import com.velocityctd.api.queue.ServerStatus;
import com.velocityctd.proxy.queue.group.VelocityServerGroup;
import com.velocityctd.proxy.queue.redis.depot.VelocityQueueDepotEntry;
import com.velocityctd.proxy.queue.redis.depot.VelocityQueueDepotService;
import com.velocityctd.proxy.queue.redis.packet.VelocityQueueSync;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.depot.player.PlayerDepotService;
import com.velocityctd.proxy.redis.depot.proxy.ProxyDepotService;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for Redis-backed group queue recovery and synchronization.
 */
class RedisGroupQueueTest {

  private static final String GROUP = "survival";
  private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

  private VelocityServer server;
  private VelocityConfiguration.Queue queueConfig;
  private VelocityRedis redis;
  private VelocityQueueDepotService depotService;
  private VelocityRegisteredServer backend;
  private VelocityQueueManager sourceManager;
  private RedisVelocityQueueManager manager;

  @BeforeEach
  void setUp() {
    VelocityQueueManager.getLastTurnedOnlineTime().clear();
    queueConfig = mock(VelocityConfiguration.Queue.class);
    VelocityConfiguration config = mock(VelocityConfiguration.class);
    when(config.getQueue()).thenReturn(queueConfig);
    when(config.getReadTimeout()).thenReturn(5000);
    when(queueConfig.getGroups()).thenReturn(Map.of(GROUP,
        new VelocityConfiguration.Queue.ServerGroupDefinition(
            GROUP, List.of("Survival-01"), SelectionStrategy.LEAST_PLAYERS, -1)));
    when(queueConfig.getMasterProxyIds()).thenReturn(List.of());
    when(queueConfig.getMaxSendRetries()).thenReturn(10);
    when(queueConfig.getBannedReason()).thenReturn(List.of());
    when(queueConfig.getNoQueueServers()).thenReturn(List.of());
    when(queueConfig.getBackendPingInterval()).thenReturn(60.0);
    when(queueConfig.getSendDelay()).thenReturn(60.0);
    when(queueConfig.getMessageDelay()).thenReturn(60.0);
    when(queueConfig.getQueueDelay()).thenReturn(0.0);

    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    when(server.getConfiguration()).thenReturn(config);
    when(server.getProxyId()).thenReturn("proxy-a");
    backend = mock(VelocityRegisteredServer.class);
    when(backend.getServerInfo()).thenReturn(
        new ServerInfo("Survival-01", new InetSocketAddress("127.0.0.1", 1)));
    when(backend.getPlayersConnected()).thenReturn(List.of());
    when(server.getServer(any(String.class))).thenReturn(Optional.empty());
    when(server.getServer("Survival-01")).thenReturn(Optional.of(backend));
    when(server.getAllServers()).thenReturn(List.of(backend));

    redis = mock(VelocityRedis.class);
    when(server.getRedis()).thenReturn(redis);
    when(redis.isShutdown()).thenReturn(false);
    depotService = mock(VelocityQueueDepotService.class);
    when(depotService.getAll()).thenReturn(List.of());
    when(redis.getQueueService()).thenReturn(depotService);
    ProxyDepotService proxyService = mock(ProxyDepotService.class);
    when(proxyService.getAllProxyIds()).thenReturn(List.of("proxy-a"));
    when(redis.getProxyService()).thenReturn(proxyService);
    PlayerDepotService playerService = mock(PlayerDepotService.class);
    when(playerService.isPlayerOnline(any(UUID.class))).thenReturn(true);
    when(redis.getPlayerService()).thenReturn(playerService);

    sourceManager = mock(VelocityQueueManager.class);
  }

  private RedisVelocityQueue sourceQueue(QueueState state, ServerStatus status) {
    RedisVelocityQueue queue = new RedisVelocityQueue(server, sourceManager, backend, state,
        GROUP, (playerId, bypass) -> Optional.of(backend));
    queue.setServerStatus(status);
    return queue;
  }

  private static void markWaiting(VelocityQueueEntry entry) throws Exception {
    Field field = VelocityQueueEntry.class.getDeclaredField("waitingForConnection");
    field.setAccessible(true);
    field.setBoolean(entry, true);
  }

  private static void invokeReload(RedisVelocityQueueManager target) throws Exception {
    Method method = RedisVelocityQueueManager.class.getDeclaredMethod("reloadFromRedis");
    method.setAccessible(true);
    method.invoke(target);
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, ScheduledTask> pendingTasks(RedisVelocityQueueManager target)
      throws Exception {
    Field field = VelocityQueueManager.class.getDeclaredField("pendingTimeoutTasks");
    field.setAccessible(true);
    return (Map<UUID, ScheduledTask>) field.get(target);
  }

  @Test
  void restoresGroupDepotQueueWithEntriesStateAndWarmup() throws Exception {
    RedisVelocityQueue source = sourceQueue(QueueState.ACTIVE, ServerStatus.WAITING);
    source.enqueue(new QueueEntryData(PLAYER, "player", 7, true, false));
    VelocityQueueEntry stored = source.getEntry(PLAYER);
    markWaiting(stored);
    when(depotService.getAll()).thenReturn(List.of(new VelocityQueueDepotEntry(source)));

    manager = new RedisVelocityQueueManager(server);

    RedisVelocityQueue restored = manager.getQueue(GROUP);
    assertEquals(GROUP, restored.getName());
    assertTrue(restored.isGroupQueue());
    assertEquals(QueueState.ACTIVE, restored.getState());
    assertEquals(ServerStatus.WAITING, restored.getServerStatus());
    assertEquals(1, restored.size());
    VelocityQueueEntry entry = restored.getEntry(PLAYER);
    assertEquals("player", entry.getUsername());
    assertEquals(7, entry.getPriority());
    assertTrue(entry.isFullBypass());
    assertFalse(entry.isWaitingForConnection(), "Stale waiting entries must be reset");
    assertTrue(VelocityQueueManager.getLastTurnedOnlineTime().containsKey(GROUP));
  }

  @Test
  void createsGroupQueueForConfiguredGroupWithoutDepotSnapshot() {
    manager = new RedisVelocityQueueManager(server);

    RedisVelocityQueue queue = manager.getQueue(GROUP);
    assertEquals(GROUP, queue.getName());
    assertTrue(queue.isGroupQueue());
    assertEquals(0, queue.size());
  }

  @Test
  void syncPacketsAreAppliedToTheLogicalGroupName() {
    manager = new RedisVelocityQueueManager(server);

    manager.handleSync(VelocityQueueSync.stateChange(GROUP, QueueState.PAUSED));
    assertEquals(QueueState.PAUSED, manager.getQueue(GROUP).getState());

    manager.handleSync(VelocityQueueSync.enqueue(
        GROUP, PLAYER, "player", 5, true, false));
    assertTrue(manager.getQueue(GROUP).contains(PLAYER));
    assertEquals(5, manager.getQueue(GROUP).getEntry(PLAYER).getPriority());
    assertTrue(manager.getQueue(GROUP).getEntry(PLAYER).isFullBypass());

    manager.handleSync(VelocityQueueSync.waitingChange(GROUP, PLAYER, true, 3, 9, false, false));
    assertTrue(manager.getQueue(GROUP).getEntry(PLAYER).isWaitingForConnection());
    assertEquals(3, manager.getQueue(GROUP).getEntry(PLAYER).getConnectionAttempts());
    assertEquals(9, manager.getQueue(GROUP).getEntry(PLAYER).getPriority());

    manager.handleSync(VelocityQueueSync.dequeue(GROUP, PLAYER));
    assertFalse(manager.getQueue(GROUP).contains(PLAYER));

    manager.handleSync(VelocityQueueSync.statusChange(GROUP, ServerStatus.FULL));
    assertEquals(ServerStatus.FULL, manager.getQueue(GROUP).getServerStatus());
  }

  @Test
  void reconnectRestoresGroupDepotAndCancelsStaleRemovalTasks() throws Exception {
    RedisVelocityQueue initial = sourceQueue(QueueState.ACTIVE, ServerStatus.ONLINE);
    when(depotService.getAll()).thenReturn(List.of(new VelocityQueueDepotEntry(initial)));
    manager = new RedisVelocityQueueManager(server);
    ScheduledTask stale = mock(ScheduledTask.class);
    pendingTasks(manager).put(PLAYER, stale);
    VelocityQueueManager.getLastTurnedOnlineTime().put(GROUP, 1L);

    RedisVelocityQueue replacement = sourceQueue(QueueState.PAUSED, ServerStatus.ONLINE);
    replacement.enqueue(new QueueEntryData(PLAYER, "player", 1, false, false));
    when(depotService.getAll()).thenReturn(List.of(new VelocityQueueDepotEntry(replacement)));

    invokeReload(manager);

    verify(stale).cancel();
    assertTrue(pendingTasks(manager).isEmpty());
    assertFalse(VelocityQueueManager.getLastTurnedOnlineTime().containsKey(GROUP));
    assertEquals(QueueState.PAUSED, manager.getQueue(GROUP).getState());
    assertTrue(manager.getQueue(GROUP).contains(PLAYER));
    verify(redis, org.mockito.Mockito.atLeastOnce()).publish(
        VelocityQueueSync.stateChange(GROUP, QueueState.PAUSED));
  }

  @Test
  void reconnectDoesNotRepublishUnchangedStatesOnNonMaster() throws Exception {
    RedisVelocityQueue initial = sourceQueue(QueueState.ACTIVE, ServerStatus.ONLINE);
    when(depotService.getAll()).thenReturn(List.of(new VelocityQueueDepotEntry(initial)));
    manager = new RedisVelocityQueueManager(server);
    RedisVelocityQueue same = sourceQueue(QueueState.ACTIVE, ServerStatus.ONLINE);
    when(depotService.getAll()).thenReturn(List.of(new VelocityQueueDepotEntry(same)));

    Field cached = RedisVelocityQueueManager.class.getDeclaredField("cachedIsMaster");
    cached.setAccessible(true);
    cached.setBoolean(manager, false);
    org.mockito.Mockito.clearInvocations(redis);

    invokeReload(manager);

    verify(redis, never()).publish(org.mockito.ArgumentMatchers.any(VelocityQueueSync.class));
  }

  @Test
  void groupQueueUsesCurrentConfigurationAfterRebuild() {
    manager = new RedisVelocityQueueManager(server);
    RedisVelocityQueue queue = manager.getQueue(GROUP);
    manager.getQueue("Survival-01").setServerStatus(ServerStatus.ONLINE);
    assertEquals(Optional.of(backend), queue.resolveTargetServer(PLAYER));

    VelocityServerGroup group = new VelocityServerGroup(GROUP, List.of("Survival-01"),
        SelectionStrategy.LEAST_PLAYERS, -1);
    assertEquals(GROUP, group.getName());
    manager.getServerGroupManager().getGroups().forEach(actual ->
        assertEquals(GROUP, actual.getName()));
  }

  @Test
  void staleWarmupTimestampsAreClearedAndReseededOnReconnect() throws Exception {
    RedisVelocityQueue initial = sourceQueue(QueueState.ACTIVE, ServerStatus.ONLINE);
    when(depotService.getAll()).thenReturn(List.of(new VelocityQueueDepotEntry(initial)));
    manager = new RedisVelocityQueueManager(server);
    VelocityQueueManager.getLastTurnedOnlineTime().put(GROUP, 1L);

    RedisVelocityQueue waiting = sourceQueue(QueueState.ACTIVE, ServerStatus.WAITING);
    when(depotService.getAll()).thenReturn(List.of(new VelocityQueueDepotEntry(waiting)));
    invokeReload(manager);

    Long seeded = VelocityQueueManager.getLastTurnedOnlineTime().get(GROUP);
    assertTrue(seeded != null && seeded > 1L);
  }
}
