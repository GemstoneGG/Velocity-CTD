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
import com.velocityctd.api.queue.QueueState;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private final VelocityServer server;
  private final VelocityQueueManager queueManager;
  private final LastServerStore lastServerStore;
  private final Object connectListener;

  private volatile Map<String, VelocityServerGroup> groups = Map.of();
  private @Nullable ScheduledTask refreshTask;

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
        recordLastServer(event.getPlayer().getUniqueId(),
            event.getServer().getServerInfo().getName());
      }
    };
    server.getEventManager().register(VelocityVirtualPlugin.INSTANCE, connectListener);

    scheduleRefresh();
  }

  /**
   * Reloads the group definitions from the current configuration.
   */
  public void rebuild() {
    Map<String, VelocityServerGroup> rebuilt = new HashMap<>();
    for (VelocityConfiguration.Queue.ServerGroupDefinition definition
        : server.getConfiguration().getQueue().getGroups().values()) {
      rebuilt.put(definition.name(), new VelocityServerGroup(definition));
    }

    this.groups = Map.copyOf(rebuilt);
  }

  /**
   * Returns whether a group with the exact given name is configured.
   *
   * @param name the group name
   * @return {@code true} when the group exists
   */
  public boolean hasGroup(String name) {
    return groups.containsKey(name);
  }

  /**
   * Returns the runtime group with the exact given name.
   *
   * @param name the group name
   * @return the group, or {@code null} when no such group is configured
   */
  public @Nullable VelocityServerGroup getGroup(String name) {
    return groups.get(name);
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
   * Chooses a backend for a player in the named group.
   *
   * @param groupName the group name
   * @param player    the player requesting a backend
   * @return the chosen backend name, or an empty optional when none is available
   */
  public Optional<String> selectBackend(String groupName, ConnectedPlayer player) {
    VelocityServerGroup group = groups.get(groupName);
    if (group == null) {
      return Optional.empty();
    }

    return selectBackend(group, player.getUniqueId());
  }

  /**
   * Chooses a backend for a player in the given group.
   *
   * @param group  the group
   * @param player the player requesting a backend
   * @return the chosen backend name, or an empty optional when none is available
   */
  public Optional<String> selectBackend(VelocityServerGroup group, ConnectedPlayer player) {
    return selectBackend(group, player.getUniqueId());
  }

  /**
   * Chooses a backend for the player with the given unique id.
   *
   * @param group    the group
   * @param playerId the player's unique id
   * @return the chosen backend name, or an empty optional when none is available
   */
  public Optional<String> selectBackend(VelocityServerGroup group, UUID playerId) {
    Optional<String> lastServer = lastServerStore.getLastServer(playerId);
    List<String> available = BackendSelector.availableBackends(
        group.getServers(),
        this::isBackendOnline,
        this::playerCount,
        group.getMaxPlayers(),
        lastServer.orElse(null));

    if (available.isEmpty()) {
      LOGGER.debug("No available backend for server group {}.", group.getName());
      return Optional.empty();
    }

    String chosen = BackendSelector.select(group.getStrategy(), available, this::playerCount,
        lastServer.orElse(null), group.getRoundRobin(), group.getRandom());
    recordLastServer(playerId, chosen);
    return Optional.of(chosen);
  }

  /**
   * Records the last backend a player connected to.
   *
   * @param playerId   the player's unique id
   * @param serverName the backend name
   */
  public void recordLastServer(UUID playerId, String serverName) {
    lastServerStore.recordLastServer(playerId, serverName);
  }

  /**
   * Updates each group queue's status from the status of its backends.
   *
   * <p>A group is online when at least one of its backends is online. The group's
   * queue is kept active unless the group name is listed as a no-queue server.</p>
   */
  public void refresh() {
    VelocityConfiguration.Queue config = server.getConfiguration().getQueue();

    for (VelocityServerGroup group : groups.values()) {
      boolean anyOnline = false;
      for (String backend : group.getServers()) {
        if (isBackendOnline(backend)) {
          anyOnline = true;
          break;
        }
      }

      VelocityQueue<?> queue;
      try {
        queue = queueManager.getQueue(group.getName());
      } catch (IllegalArgumentException exception) {
        LOGGER.debug("Skipping refresh for group {} because no queue is available.", group.getName());
        continue;
      }

      queue.setServerStatus(anyOnline ? ONLINE : ServerStatus.OFFLINE);
      boolean inactive = config.getNoQueueServers().contains(group.getName());
      queue.setState(inactive ? QueueState.INACTIVE : QueueState.ACTIVE);
    }
  }

  /**
   * Cancels the refresh task and unregisters the reconnect listener.
   */
  public void shutdown() {
    if (refreshTask != null) {
      refreshTask.cancel();
      refreshTask = null;
    }

    server.getEventManager().unregisterListener(VelocityVirtualPlugin.INSTANCE, connectListener);
  }

  private void scheduleRefresh() {
    double interval = server.getConfiguration().getQueue().getBackendPingInterval();
    long seconds = Math.max(1L, (long) Math.ceil(interval));

    refreshTask = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::refresh)
        .delay(seconds, TimeUnit.SECONDS)
        .repeat(seconds, TimeUnit.SECONDS)
        .schedule();
  }

  private boolean isBackendOnline(String backendName) {
    if (server.getServer(backendName).isEmpty()) {
      return false;
    }

    try {
      return queueManager.getQueue(backendName).getServerStatus() == ONLINE;
    } catch (IllegalArgumentException exception) {
      return false;
    }
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
}
