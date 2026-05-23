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

import static com.velocityctd.api.queue.QueueState.ACTIVE;
import static com.velocityctd.api.queue.QueueState.PAUSED;
import static com.velocityctd.api.queue.ServerStatus.FULL;
import static com.velocityctd.api.queue.ServerStatus.OFFLINE;
import static com.velocityctd.api.queue.ServerStatus.ONLINE;
import static com.velocityctd.api.queue.ServerStatus.WAITING;
import static com.velocityctd.proxy.permission.PermissionUtils.findHighestPermissionValue;

import com.velocityctd.api.queue.QueueManager;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.ServerStatus;
import com.velocityctd.proxy.queue.util.QueueComponents;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Local implementation of {@link QueueManager}.
 */
public class VelocityQueueManager implements QueueManager {

  private static final Logger LOGGER = LogManager.getLogger(VelocityQueueManager.class);

  /**
   * Timestamp (ms) of when each server last transitioned from OFFLINE to WAITING.
   */
  protected static final Map<String, Long> LAST_TURNED_ONLINE_TIME = new ConcurrentHashMap<>();

  /**
   * How many ticks between switching to displaying the next queue on the round-robin action-bar display.
   */
  private static final int TICKS_PER_ACTION_BAR_CHANGE = 2;

  protected final VelocityServer server;

  /**
   * Local in-memory queues keyed by server name.
   */
  protected final Map<String, VelocityQueue<?>> queues = new ConcurrentHashMap<>();

  /**
   * Holds all scheduled tasks to remove players from all queues (after a player's timeout).
   * Stored such that they can be cancelled on join.
   */
  protected final Map<UUID, ScheduledTask> pendingTimeoutTasks = new ConcurrentHashMap<>();

  private @Nullable ScheduledTask transferTask;
  private @Nullable ScheduledTask actionBarTask;
  private @Nullable ScheduledTask backendHandshakeTask;

  /**
   * Monotonically increasing tick counter used for the round-robin action-bar display.
   */
  private int actionBarTick = 0;

  /**
   * Constructs a new {@link VelocityQueueManager} and starts periodic tasks.
   *
   * <p>The constructor calls {@link #preInitialize()} before filling in server queues
   * so that subclasses can load persisted state (e.g. from the Redis depot) before the
   * gap-fill loop runs. {@link #postInitialize()} is called after scheduling so
   * subclasses can register reconnect listeners or other post-start hooks.</p>
   *
   * @param server the proxy instance
   */
  public VelocityQueueManager(@NotNull VelocityServer server) {
    this.server = server;

    preInitialize();

    // Ensure every registered server has a queue (fills gaps not covered by preInitialize).
    for (VelocityRegisteredServer rs : server.getAllServers()) {
      String name = rs.getServerInfo().getName();
      queues.computeIfAbsent(name, n -> {
        boolean inactive = server.getConfiguration().getQueue()
            .getNoQueueServers().contains(n);
        return createQueue(rs, inactive ? QueueState.INACTIVE : QueueState.ACTIVE);
      });
    }

    rescheduleTasks();

    postInitialize();
  }

  /**
   * Called at the start of the constructor, before the server queue fill-gap loop.
   */
  protected void preInitialize() {
    // no-op
  }

  /**
   * Called at the end of the constructor, after {@link #rescheduleTasks()}.
   */
  protected void postInitialize() {
    // no-op
  }

  /**
   * Returns {@code true} if this proxy should act as the queue master.
   * Always {@code true} in local (non-Redis) mode.
   */
  @Override
  public boolean isMasterProxy() {
    return true;
  }

  /**
   * Checks whether the player with the given UUID is online on this proxy.
   */
  protected boolean isPlayerOnline(UUID uuid) {
    return server.getPlayer(uuid).isPresent();
  }

  /**
   * Creates a new queue for the given backend server.
   */
  protected VelocityQueue<?> createQueue(VelocityRegisteredServer rs, QueueState state) {
    return new LocalVelocityQueue(server, this, rs, state);
  }

  /**
   * Sends an action-bar update for the given queue entry.
   */
  protected void sendActionBar(VelocityQueueEntry entry) {
    Component component = QueueComponents.createActionbarComponent(entry);
    if (component != null) {
      server.getPlayer(entry.getUniqueId()).ifPresent(p -> p.sendActionBar(component));
    }
  }

  @Override
  public void reload() {
    rescheduleTasks();
  }

  @Override
  public void teardown() {
    cancelTasks();
    queues.values().forEach(VelocityQueue::teardown);
  }

  @Override
  public @NotNull VelocityQueue<?> getQueue(@NotNull String serverName) {
    VelocityQueue<?> existing = queues.get(serverName);
    if (existing != null) {
      return existing;
    }

    VelocityRegisteredServer rs = server.getServer(serverName)
        .orElseThrow(() -> new IllegalArgumentException("Unknown server: " + serverName));

    return queues.computeIfAbsent(serverName, n -> {
      boolean inactive = server.getConfiguration().getQueue().getNoQueueServers().contains(n);
      return createQueue(rs, inactive ? QueueState.INACTIVE : QueueState.ACTIVE);
    });
  }

  @Override
  public @NotNull Collection<VelocityQueue<?>> getQueues() {
    return Collections.unmodifiableCollection(queues.values());
  }

  @Override
  public @Nullable VelocityQueue<?> getQueueFor(@NotNull UUID uniqueId) {
    for (VelocityQueue<?> q : queues.values()) {
      if (q.contains(uniqueId)) {
        return q;
      }
    }
    return null;
  }

  @Override
  public void queue(@NotNull Player player, @NotNull RegisteredServer targetServer) {
    queue((ConnectedPlayer) player, (VelocityRegisteredServer) targetServer);
  }

  public void queue(@NotNull ConnectedPlayer player, @NotNull VelocityRegisteredServer targetServer) {
    String targetName = targetServer.getServerInfo().getName();
    VelocityQueue<?> queue = getQueue(targetName);

    VelocityConfiguration.Queue config = server.getConfiguration().getQueue();
    if (!config.isEnabled() || player.hasPermission("velocity.queue.bypass")) {
      player.createConnectionRequest(targetServer).connectWithIndication();
      return;
    }

    if (queue.contains(player)) {
      player.sendMessage(Component.translatable("velocity.queue.error.already-queued")
          .arguments(Component.text(targetName)));
      return;
    }

    if (!config.isAllowMultiQueue()) {
      for (VelocityQueue<?> q : queues.values()) {
        if (q.contains(player)) {
          q.dequeue(player);
          player.sendMessage(Component.translatable("velocity.queue.error.queued-swap")
              .arguments(
                  Argument.string("from", q.getName()),
                  Argument.string("to", targetName)));
          break;
        }
      }
    }

    if (queue.getState() == PAUSED && !config.isAllowPausedQueueJoining()) {
      player.sendMessage(Component.translatable("velocity.queue.error.paused")
          .arguments(Component.text(targetName)));
      return;
    }

    if (!player.checkVersionCompatibility(targetServer)) {
      return;
    }

    queue.enqueue(player);
    player.sendMessage(Component.translatable("velocity.queue.command.queued")
        .arguments(Component.text(targetName)));

    // If a queue-server is configured, move the player there if they aren't already on it
    String queueServerName = config.getQueueServer();
    if (!queueServerName.isEmpty()) {
      server.getServer(queueServerName).ifPresentOrElse(queueServer -> {
        player.getCurrentServer().ifPresent(currentServer -> {
          if (!currentServer.getServerInfo().getName().equals(queueServerName)) {
            player.createConnectionRequest(queueServer).connectWithIndication();
          }
        });
      }, () -> LOGGER.warn("Queue server '{}' is configured but not registered!", queueServerName));
    }
  }

  @Override
  public void removePlayerEntirely(@NotNull UUID uniqueId) {
    for (VelocityQueue<?> queue : queues.values()) {
      if (queue.contains(uniqueId)) {
        queue.dequeue(uniqueId);
      }
    }
  }

  /**
   * Should be called when a player leaves a backend server on any proxy.
   */
  public void onGlobalBackendLeave(@NotNull String serverName, long nowMillis) {
    if (!isMasterProxy()) {
      return;
    }

    VelocityQueue<?> queue = queues.get(serverName);
    if (queue != null) {
      queue.getEtaTracker().ifPresent(t -> t.recordBackendPlayerLeave(nowMillis));
    }
  }

  /**
   * Should only be called when a player connects to this specific proxy.
   */
  public void onLocalPlayerConnect(@NotNull ConnectedPlayer player) {
    ScheduledTask timeoutTask = pendingTimeoutTasks.remove(player.getUniqueId());
    if (timeoutTask != null) {
      timeoutTask.cancel();
      // Player reconnected within the timeout window -> Clear the offline state from all entries.
      queues.values().forEach(q -> {
        VelocityQueueEntry entry = q.getEntry(player.getUniqueId());
        if (entry != null) {
          entry.clearOffline();
        }
      });
    }
  }

  /**
   * Should only be called when a player disconnects to this specific proxy.
   */
  public void onLocalPlayerDisconnect(@NotNull ConnectedPlayer player) {
    if (!isQueued(player)) {
      return;
    }

    if (server.isShuttingDown()) {
      removePlayerEntirely(player);
      return;
    }

    int timeout = getTimeoutInSeconds(player);
    if (timeout <= 0) {
      LOGGER.debug("Removing player {} from all queues immediately (no timeout).", player.getUsername());
      removePlayerEntirely(player);
    } else {
      LOGGER.debug("Removing player {} from all queues in {} second(s) (has timeout).", player.getUsername(), timeout);
      UUID playerUniqueId = player.getUniqueId();

      // Record when the player went offline and their timeout so that, if all proxies are
      // force-killed, the next startup can compute the correct remaining removal delay.
      long offlineSince = System.currentTimeMillis();
      queues.values().forEach(q -> {
        VelocityQueueEntry entry = q.getEntry(playerUniqueId);
        if (entry != null) {
          entry.setOffline(offlineSince, timeout);
        }
      });

      ScheduledTask task = server.getScheduler()
          .buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
            if (!isPlayerOnline(playerUniqueId)) {
              removePlayerEntirely(playerUniqueId);
            }
          })
          .delay(timeout, TimeUnit.SECONDS)
          .schedule();

      pendingTimeoutTasks.put(playerUniqueId, task);
    }
  }

  private void rescheduleTasks() {
    cancelTasks();

    scheduleActionBarTask();
    scheduleTransferTask();
    scheduleBackendHandshakeTask();
  }

  private void scheduleTransferTask() {
    VelocityConfiguration.Queue config = server.getConfiguration().getQueue();
    transferTask = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::runTransfer)
        .delay((long) (config.getSendDelay() * 1000), TimeUnit.MILLISECONDS)
        .repeat((long) (config.getSendDelay() * 1000), TimeUnit.MILLISECONDS)
        .schedule();
  }

  private void scheduleBackendHandshakeTask() {
    VelocityConfiguration.Queue config = server.getConfiguration().getQueue();
    backendHandshakeTask = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::pingBackends)
        .delay((long) (config.getBackendPingInterval() * 1000), TimeUnit.MILLISECONDS)
        .repeat((long) (config.getBackendPingInterval() * 1000), TimeUnit.MILLISECONDS)
        .schedule();
  }

  private void scheduleActionBarTask() {
    VelocityConfiguration.Queue config = server.getConfiguration().getQueue();
    actionBarTask = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, this::sendActionBars)
        .delay((long) (config.getMessageDelay() * 1000), TimeUnit.MILLISECONDS)
        .repeat((long) (config.getMessageDelay() * 1000), TimeUnit.MILLISECONDS)
        .schedule();
  }

  private void cancelTasks() {
    if (transferTask != null) {
      transferTask.cancel();
      transferTask = null;
    }

    if (actionBarTask != null) {
      actionBarTask.cancel();
      actionBarTask = null;
    }

    if (backendHandshakeTask != null) {
      backendHandshakeTask.cancel();
      backendHandshakeTask = null;
    }
  }

  private void runTransfer() {
    if (!isMasterProxy()) {
      return;
    }

    Set<UUID> transferredThisTick = new HashSet<>();

    for (VelocityQueue<?> queue : queues.values()) {
      if (queue.getState() != ACTIVE
          || !queue.getServerStatus().isActive()
          || queue.size() == 0) {
        continue;
      }

      VelocityQueueEntry candidate = queue.findFirst(e ->
          !transferredThisTick.contains(e.getUniqueId())
              && (queue.getServerStatus() != FULL || e.isFullBypass())
              && !e.isWaitingForConnection());

      if (candidate == null) {
        continue;
      }

      if (isPlayerOnline(candidate.getUniqueId())) {
        queue.transferEntry(candidate);
        transferredThisTick.add(candidate.getUniqueId());
      } else {
        queue.removeEntry(candidate);
      }
    }
  }

  private void pingBackends() {
    if (!isMasterProxy()) {
      return;
    }

    for (VelocityQueue<?> queue : queues.values()) {
      VelocityRegisteredServer rs = server.getServer(queue.getName()).orElse(null);
      if (rs == null) {
        continue;
      }

      rs.ping().orTimeout(3, TimeUnit.SECONDS).whenComplete((result, th) -> {
        if (th != null) {
          queue.getEtaTracker().ifPresent(VelocityEtaTracker::reset);
          queue.setServerStatus(OFFLINE);
          return;
        }

        queue.getEtaTracker().ifPresent(
            t -> result.getPlayers().ifPresent(t::recordBackendPing));

        boolean serverFull = result.getPlayers()
            .map(p -> p.getMax() > 0 && p.getOnline() >= p.getMax())
            .orElse(false);
        ServerStatus activeStatus = serverFull ? FULL : ONLINE;

        if (queue.getServerStatus() == OFFLINE) {
          queue.setServerStatus(WAITING);
          LAST_TURNED_ONLINE_TIME.put(queue.getName(), System.currentTimeMillis());
        }

        if (queue.getServerStatus().isActive()) {
          queue.setServerStatus(activeStatus);
          return;
        }

        // Still WAITING - check warmup delay
        Long lastOnline = LAST_TURNED_ONLINE_TIME.get(queue.getName());
        if (lastOnline != null) {
          double queueDelay = server.getConfiguration().getQueue().getQueueDelay() * 1000;
          if (System.currentTimeMillis() >= lastOnline + queueDelay) {
            queue.setServerStatus(activeStatus);
          }
        }
      }).exceptionally(th -> {
        queue.getEtaTracker().ifPresent(VelocityEtaTracker::reset);
        queue.setServerStatus(OFFLINE);
        return null;
      });
    }
  }

  private void sendActionBars() {
    if (!isMasterProxy()) {
      return;
    }

    // Collect all entries per player UUID (player may be in multiple queues)
    Map<UUID, List<VelocityQueueEntry>> byPlayer = new HashMap<>();

    for (VelocityQueue<?> queue : queues.values()) {
      for (VelocityQueueEntry entry : queue.getInternalEntries()) {
        byPlayer.computeIfAbsent(entry.getUniqueId(), k -> new ArrayList<>(2)).add(entry);
      }
    }

    for (List<VelocityQueueEntry> entries : byPlayer.values()) {
      if (entries.size() > 1) {
        entries.sort(Comparator.comparing(e -> e.getQueue().getName()));
      }

      int index = (actionBarTick / TICKS_PER_ACTION_BAR_CHANGE) % entries.size();
      sendActionBar(entries.get(index));
    }

    actionBarTick++;
  }

  private int getTimeoutInSeconds(ConnectedPlayer player) {
    if (player.hasPermission("velocity.queue.timeout.exempt")) {
      return 0;
    }

    return findHighestPermissionValue(player, "velocity.queue.timeout.", 86_400)
        .orElse(0);
  }
}
