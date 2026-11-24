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

import static com.velocityctd.api.queue.ServerStatus.ONLINE;

import com.velocityctd.api.queue.LastServerStore;
import com.velocityctd.api.queue.ServerStatus;
import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocityctd.proxy.queue.VelocityQueueManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the configured server groups and keeps their shared queues in sync with the
 * backends they contain.
 */
public final class ServerGroupManager {

  private static final Logger LOGGER = LogManager.getLogger(ServerGroupManager.class);

  /**
   * Warmup timestamps shared with {@link VelocityQueueManager} so group queues follow the
   * same OFFLINE to WAITING transition as physical backend queues. The map is shared through
   * {@link VelocityQueueManager#getLastTurnedOnlineTime()} so group and physical health checks
   * honour the same warmup window.
   */
  private static final Map<String, Long> LAST_ONLINE_TIMES = VelocityQueueManager.getLastTurnedOnlineTime();

  private final VelocityServer server;
  private final VelocityQueueManager queueManager;
  private final LastServerStore lastServerStore;
  private final Object connectListener;

  private volatile Map<String, VelocityServerGroup> groups = Map.of();
  private volatile @Nullable ScheduledTask refreshTask;
  private long refreshIntervalSeconds = -1L;
  private boolean shutDown;
  private boolean listenerRegistered;

  /**
   * Creates a group manager, registers its reconnect listener and starts its refresh task.
   *
   * @param server      the proxy instance
   * @param queueManager the owning queue manager
   */
  public ServerGroupManager(@NotNull VelocityServer server,
                            @NotNull VelocityQueueManager queueManager) {
    this.server = server;
    this.queueManager = queueManager;
    this.lastServerStore = LastServerStores.create(server.getRedis());

    rebuild();

    this.connectListener = new Object() {
      @Subscribe
      public void onConnect(ServerConnectedEvent event) {
        recordConnection(event.getPlayer().getUniqueId(),
            event.getServer().getServerInfo().getName());
      }
    };
    server.getEventManager().register(VelocityVirtualPlugin.INSTANCE, connectListener);
    listenerRegistered = true;
  }

  /**
   * Reloads the group definitions from the current configuration and reschedules the
   * refresh task if the configured backend ping interval changed.
   */
  public void rebuild() {
    Set<String> registeredNames = new HashSet<>();
    for (VelocityRegisteredServer registered : server.getAllServers()) {
      registeredNames.add(normalize(registered.getServerInfo().getName()));
    }

    Map<String, VelocityServerGroup> rebuilt = new HashMap<>();
    Set<String> usedNames = new HashSet<>();
    for (VelocityConfiguration.Queue.ServerGroupDefinition definition
        : server.getConfiguration().getQueue().getGroups().values()) {
      String name = definition.name() == null ? "" : definition.name().trim();
      if (name.isEmpty()) {
        LOGGER.warn("Ignoring a server group with an empty name.");
        continue;
      }

      if (registeredNames.contains(normalize(name))) {
        LOGGER.warn("Ignoring server group '{}': a registered backend server uses the same "
            + "name (case-insensitive); physical queues take precedence.", name);
        continue;
      }

      if (!usedNames.add(normalize(name))) {
        LOGGER.warn("Ignoring duplicate server group '{}'.", name);
        continue;
      }

      String canonical = normalize(name);
      rebuilt.put(canonical, new VelocityServerGroup(canonical, definition.servers(),
          definition.strategy(), definition.maxPlayers()));
    }

    this.groups = Map.copyOf(rebuilt);
    scheduleRefresh();
  }

  /**
   * Returns whether a group with the exact given name is configured.
   *
   * @param name the group name
   * @return {@code true} when the group exists
   */
  public boolean hasGroup(String name) {
    return groups.containsKey(normalize(name));
  }

  /**
   * Returns the canonical key of a configured group, allowing case-insensitive lookups.
   *
   * @param name the destination name supplied by a player or command
   * @return the canonical group name, or {@code null} when no such group exists
   */
  public @Nullable String canonicalName(String name) {
    String key = normalize(name);
    return groups.containsKey(key) ? key : null;
  }

  /**
   * Returns the runtime group with the exact given name.
   *
   * @param name the group name
   * @return the group, or {@code null} when no such group is configured
   */
  public @Nullable VelocityServerGroup getGroup(String name) {
    return groups.get(normalize(name));
  }

  /**
   * Returns all configured groups.
   *
   * @return an immutable collection of groups
   */
  public Collection<VelocityServerGroup> getGroups() {
    return groups.values();
  }

  /**
   * Chooses a backend for a player in the named group without full-server bypass.
   *
   * @param groupName the group name
   * @param player    the player requesting a backend
   * @return the chosen backend name, or an empty optional when none is available
   */
  public Optional<String> selectBackend(String groupName, ConnectedPlayer player) {
    return selectBackend(groupName, player.getUniqueId(), false);
  }

  /**
   * Chooses a backend for the player with the given unique id in the named group without
   * full-server bypass.
   *
   * @param groupName the group name
   * @param playerId  the player's unique id
   * @return the chosen backend name, or an empty optional when none is available
   */
  public Optional<String> selectBackend(String groupName, UUID playerId) {
    return selectBackend(groupName, playerId, false);
  }

  /**
   * Chooses a backend for the player with the given unique id in the named group.
   *
   * @param groupName the group name
   * @param playerId  the player's unique id
   * @param allowFull whether full backends are eligible (full-server bypass)
   * @return the chosen backend name, or an empty optional when none is available
   */
  public Optional<String> selectBackend(String groupName, UUID playerId, boolean allowFull) {
    VelocityServerGroup group = groups.get(groupName);
    if (group == null) {
      return Optional.empty();
    }

    return selectBackend(group, playerId, allowFull);
  }

  /**
   * Chooses a backend for a player in the given group without full-server bypass.
   *
   * @param group  the group
   * @param player the player requesting a backend
   * @return the chosen backend name, or an empty optional when none is available
   */
  public Optional<String> selectBackend(VelocityServerGroup group, ConnectedPlayer player) {
    return selectBackend(group, player.getUniqueId(), false);
  }

  /**
   * Chooses a backend for the player with the given unique id without full-server bypass.
   *
   * @param group    the group
   * @param playerId the player's unique id
   * @return the chosen backend name, or an empty optional when none is available
   */
  public Optional<String> selectBackend(VelocityServerGroup group, UUID playerId) {
    return selectBackend(group, playerId, false);
  }

  /**
   * Chooses a backend for the player with the given unique id.
   *
   * <p>Selection is stateless apart from the strategy's own counters: the group-scoped
   * last-server value is only read as a hint and is never written here. It is recorded
   * when the player actually connects to a backend.</p>
   *
   * @param group     the group
   * @param playerId  the player's unique id
   * @param allowFull whether full backends are eligible (full-server bypass)
   * @return the chosen backend name, or an empty optional when none is available
   */
  public Optional<String> selectBackend(VelocityServerGroup group, UUID playerId,
                                        boolean allowFull) {
    Optional<String> lastServer = lastServerStore.getLastServer(playerId, group.getName());
    List<String> available = BackendSelector.availableBackends(
        group.getServers(), this::isBackendOnline, this::playerCount,
        group.getMaxPlayers(), lastServer.orElse(null), allowFull);

    if (available.isEmpty()) {
      LOGGER.debug("No available backend for server group {}.", group.getName());
      return Optional.empty();
    }

    return Optional.of(BackendSelector.select(group.getStrategy(), available, this::playerCount,
        lastServer.orElse(null), group.getRoundRobin(), group.getRandom()));
  }

  /**
   * Records a legacy, unscoped last-server value.
   *
   * <p>Normal group selection records the value through the group-scoped methods instead.
   * This method remains for callers using the original API.</p>
   *
   * @param playerId   the player's unique id
   * @param serverName the backend name
   */
  public void recordLastServer(UUID playerId, String serverName) {
    lastServerStore.recordLastServer(playerId, serverName);
  }

  /**
   * Refreshes the status of every group queue from the reachable state of its backends.
   *
   * <p>Only the master proxy computes and publishes status. Queue states (including an
   * administrator's paused state) are never touched here.</p>
   */
  public void refresh() {
    if (!queueManager.isMasterProxy()) {
      return;
    }

    long warmupMillis = Math.max(0L,
        (long) (server.getConfiguration().getQueue().getQueueDelay() * 1000L));
    long now = System.currentTimeMillis();

    for (VelocityServerGroup group : groups.values()) {
      VelocityQueue<?> queue = groupQueue(group.getName());
      if (queue == null) {
        continue;
      }

      ServerStatus desired = aggregateStatus(group, queue.getServerStatus(), now, warmupMillis);
      if (queue.getServerStatus() != desired) {
        queue.setServerStatus(desired);
      }
    }
  }

  /**
   * Cancels the refresh task and unregisters the connect listener. Subsequent calls do
   * nothing.
   */
  public void shutdown() {
    if (shutDown) {
      return;
    }
    shutDown = true;

    cancelRefreshTask();
    if (listenerRegistered) {
      server.getEventManager().unregisterListener(VelocityVirtualPlugin.INSTANCE, connectListener);
      listenerRegistered = false;
    }
  }

  private void recordConnection(UUID playerId, String serverName) {
    if (serverName == null || serverName.isEmpty()) {
      return;
    }

    // A queue/lobby server that is not a group member is ignored automatically: only
    // groups that explicitly contain the connected backend record a value for it.
    for (VelocityServerGroup group : groups.values()) {
      if (group.getServers().contains(serverName)) {
        lastServerStore.recordLastServer(playerId, group.getName(), serverName);
      }
    }
  }

  private ServerStatus aggregateStatus(VelocityServerGroup group, ServerStatus current,
                                       long now, long warmupMillis) {
    boolean anyActive = false;
    boolean anyBelowCapacity = false;
    for (String backend : group.getServers()) {
      ServerStatus status = backendStatus(backend);
      if (!status.isActive()) {
        continue;
      }

      anyActive = true;
      if (status != ServerStatus.FULL) {
        anyBelowCapacity = true;
      }
    }

    if (!anyActive) {
      LAST_ONLINE_TIMES.remove(group.getName());
      return ServerStatus.OFFLINE;
    }

    ServerStatus active = anyBelowCapacity ? ONLINE : ServerStatus.FULL;
    if (current.isActive()) {
      return active;
    }

    if (current == ServerStatus.OFFLINE) {
      LAST_ONLINE_TIMES.put(group.getName(), now);
    }

    Long started = LAST_ONLINE_TIMES.get(group.getName());
    if (started != null && now < started + warmupMillis) {
      return ServerStatus.WAITING;
    }
    return active;
  }

  private @Nullable VelocityQueue<?> groupQueue(String name) {
    try {
      return queueManager.getQueue(name);
    } catch (IllegalArgumentException exception) {
      LOGGER.debug("Skipping refresh for group {} because no queue is available.", name);
      return null;
    }
  }

  private void scheduleRefresh() {
    if (shutDown) {
      return;
    }

    double interval = server.getConfiguration().getQueue().getBackendPingInterval();
    long seconds = Math.max(1L, (long) Math.ceil(interval));
    ScheduledTask task = refreshTask;
    if (task != null && refreshIntervalSeconds == seconds) {
      return;
    }

    cancelRefreshTask();
    refreshTask = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::refresh)
        .delay(seconds, TimeUnit.SECONDS)
        .repeat(seconds, TimeUnit.SECONDS)
        .schedule();
    refreshIntervalSeconds = seconds;
  }

  private void cancelRefreshTask() {
    ScheduledTask task = refreshTask;
    refreshTask = null;
    if (task != null) {
      task.cancel();
    }
  }

  private ServerStatus backendStatus(String backendName) {
    if (server.getServer(backendName).isEmpty()) {
      return ServerStatus.OFFLINE;
    }

    try {
      return queueManager.getQueue(backendName).getServerStatus();
    } catch (IllegalArgumentException exception) {
      return ServerStatus.OFFLINE;
    }
  }

  private boolean isBackendOnline(String backendName) {
    return backendStatus(backendName).isActive();
  }

  private int playerCount(String backendName) {
    if (server.getRedis() != null && !server.getRedis().isShutdown()) {
      try {
        return server.getRedis().getPlayerService().getPlayerCountInServer(backendName);
      } catch (Exception exception) {
        LOGGER.debug("Unable to read the Redis player count for {}.", backendName, exception);
      }
    }

    return server.getServer(backendName)
        .map(RegisteredServer::getPlayersConnected)
        .map(connected -> connected.size())
        .orElse(Integer.MAX_VALUE);
  }

  private static String normalize(String name) {
    return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
  }
}
