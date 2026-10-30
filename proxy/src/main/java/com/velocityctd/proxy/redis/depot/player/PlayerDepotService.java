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

package com.velocityctd.proxy.redis.depot.player;

import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.data.VelocityKick;
import com.velocityctd.proxy.redis.depot.AbstractDepotService;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Represents an extension of the {@link AbstractDepotService} for the player depot, including
 * functionality to track certain information about a single player, or multiple players.
 */
public final class PlayerDepotService extends AbstractDepotService<UUID, PlayerEntry> {

  private static final Logger LOGGER = LogManager.getLogger(PlayerDepotService.class);

  /**
   * How long teardown waits for running depot tasks.
   */
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 5L;

  /**
   * The number of executors depot tasks are spread over.
   */
  private static final int EXECUTOR_COUNT = 8;

  /**
   * How many waiting tasks on one executor count as a backlog worth a warning.
   */
  private static final int BACKLOG_WARNING_THRESHOLD = 256;

  /**
   * The least time between two backlog warnings.
   */
  private static final long BACKLOG_WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30L);

  /**
   * The Redis manager used to coordinate multi-proxy player synchronization.
   */
  private final VelocityRedis redis;

  /**
   * The proxy server instance associated with this depot service.
   */
  private final VelocityServer server;

  /**
   * Scheduled task responsible for periodically updating the total count of players
   * present across all proxies.
   */
  private final ScheduledTask updateTotalPlayerCountTask;

  /**
   * Scheduled task responsible for synchronizing player entries between Redis and
   * the current proxy, ensuring consistency with online players.
   */
  private final ScheduledTask syncPlayerEntriesTask;

  /**
   * The number of players currently recorded across all proxies.
   */
  private int totalPlayerCount = 0;

  /**
   * The number of players on each server across all proxies, as of the last player entry sync.
   */
  private volatile Map<String, Integer> serverPlayerCounts = Map.of();

  /**
   * Every player entry across all proxies, as of the last player entry sync.
   */
  private volatile List<PlayerEntry> syncedPlayerEntries = List.of();

  /**
   * Executors running the depot's Redis calls off the network threads. A player's tasks always
   * run on the same executor, so they stay in order.
   */
  private final ThreadPoolExecutor[] executors = createExecutors();

  /**
   * When the last backlog warning was logged, from {@link System#nanoTime()}.
   */
  private final AtomicLong lastBacklogWarning = new AtomicLong(System.nanoTime() - BACKLOG_WARNING_INTERVAL_NANOS);

  /**
   * Constructs a new {@link PlayerDepotService}.
   *
   * @param redis the {@link VelocityRedis} instance
   */
  public PlayerDepotService(@NotNull VelocityRedis redis) {
    super(PlayerEntry.class, redis.getProvider());

    this.redis = redis;
    this.server = redis.getServer();

    this.updateTotalPlayerCountTask = redis.getServer().getScheduler()
            .buildTask(VelocityVirtualPlugin.INSTANCE, this::updateTotalPlayerCount)
            .repeat(Duration.ofMillis(250L))
            .schedule();

    this.syncPlayerEntriesTask = redis.getServer().getScheduler()
            .buildTask(VelocityVirtualPlugin.INSTANCE, this::syncPlayerEntries)
            .repeat(Duration.ofSeconds(1L))
            .schedule();
  }

  @Override
  public void teardown() {
    for (ExecutorService executor : this.executors) {
      executor.shutdown();
    }

    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SHUTDOWN_TIMEOUT_SECONDS);
    try {
      for (ExecutorService executor : this.executors) {
        final long remaining = deadline - System.nanoTime();
        if (remaining <= 0 || !executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
          LOGGER.warn("Player depot tasks did not finish within {} seconds, interrupting them",
              SHUTDOWN_TIMEOUT_SECONDS);
          break;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Queued tasks return early once Redis is shut down, so only running tasks are left. Every
    // write checks the shutdown flag first, so interrupting them is safe.
    for (ExecutorService executor : this.executors) {
      executor.shutdownNow();
    }

    try {
      for (ExecutorService executor : this.executors) {
        executor.awaitTermination(1L, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Also covers disconnects that returned early because of the shutdown.
    for (PlayerEntry entry : this.getPlayerEntriesOnProxy(this.redis.getProxyId())) {
      this.depot.remove(entry.getUniqueId());
    }

    if (this.updateTotalPlayerCountTask != null) {
      this.updateTotalPlayerCountTask.cancel();
    }

    if (this.syncPlayerEntriesTask != null) {
      this.syncPlayerEntriesTask.cancel();
    }
  }

  /**
   * Runs {@link #onPlayerConnect(ConnectedPlayer)} on the player's depot executor.
   *
   * @param player the player that connected
   * @return a future completing with whether the player was added to the depot
   */
  public CompletableFuture<Boolean> onPlayerConnectAsync(ConnectedPlayer player) {
    try {
      return CompletableFuture.supplyAsync(() -> this.onPlayerConnect(player),
          this.executorFor(player.getUniqueId()));
    } catch (RejectedExecutionException e) {
      // Shutting down, the login can't be recorded.
      player.disconnect(Component.translatable("velocity.kick.shutdown"));
      return CompletableFuture.completedFuture(false);
    }
  }

  /**
   * Removes the player's entry on the player's depot executor.
   *
   * @param player the player that disconnected
   */
  public void onPlayerDisconnectAsync(ConnectedPlayer player) {
    // Must be checked while the identity lock is held, a reconnect may be registered by the time
    // the task runs.
    if (this.isReplaced(player)) {
      return;
    }

    this.executeAsync(player, "disconnect", () -> this.removeOwnEntry(player));
  }

  /**
   * Removes the entry of a login whose connection closed during the duplicate check.
   *
   * @param player the player whose login was aborted
   */
  public void onPlayerLoginAbortedAsync(ConnectedPlayer player) {
    this.executeAsync(player, "aborted login", () -> {
      // A newer connection owns the entry. The aborted player itself can still be registered
      // here, since its teardown may not have run yet when the closed channel was seen.
      if (!this.isReplaced(player)) {
        this.removeOwnEntry(player);
      }
    });
  }

  /**
   * Runs {@link #onPlayerSwitchServer(ConnectedPlayer, String)} on the player's depot executor.
   *
   * @param player the player that switched servers
   * @param serverName the name of the server that the player switched to
   */
  public void onPlayerSwitchServerAsync(ConnectedPlayer player, String serverName) {
    this.executeAsync(player, "server switch", () -> this.onPlayerSwitchServer(player, serverName));
  }

  private void executeAsync(ConnectedPlayer player, String action, Runnable task) {
    try {
      this.executorFor(player.getUniqueId()).execute(() -> {
        try {
          task.run();
        } catch (RuntimeException e) {
          LOGGER.error("Failed to update the player depot for the {} of {}", action,
              player.getUsername(), e);
        }
      });
    } catch (RejectedExecutionException ignored) {
      // Shutting down, teardown removes this proxy's entries.
    }
  }

  private ExecutorService executorFor(UUID uniqueId) {
    final int index = Math.floorMod(uniqueId.hashCode(), this.executors.length);
    final ThreadPoolExecutor executor = this.executors[index];
    final int waiting = executor.getQueue().size();
    if (waiting >= BACKLOG_WARNING_THRESHOLD) {
      this.warnBacklog(index, waiting);
    }

    return executor;
  }

  /**
   * Logs that an executor is falling behind, at most once per interval. Logins wait in the same
   * line as switches and quits, so a slow Redis shows up here before it shows up as login lag.
   */
  private void warnBacklog(int index, int waiting) {
    final long now = System.nanoTime();
    final long last = this.lastBacklogWarning.get();
    if (now - last >= BACKLOG_WARNING_INTERVAL_NANOS && this.lastBacklogWarning.compareAndSet(last, now)) {
      LOGGER.warn("Player depot executor #{} has {} tasks waiting; Redis may be slow or unreachable, "
          + "and logins on this executor wait for them", index, waiting);
    }
  }

  private static ThreadPoolExecutor[] createExecutors() {
    final ThreadPoolExecutor[] executors = new ThreadPoolExecutor[EXECUTOR_COUNT];
    for (int i = 0; i < EXECUTOR_COUNT; i++) {
      // A single-thread executor whose queue can be read for the backlog warning.
      executors[i] = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
          Thread.ofPlatform().name("Velocity-CTD - Player Depot #" + i).daemon(true).factory());
    }

    return executors;
  }

  /**
   * Called when a {@link ConnectedPlayer} connects to the proxy.
   *
   * @param player the player that connected
   * @return {@code true} if the player was successfully added to the depot, {@code false} otherwise
   */
  public boolean onPlayerConnect(ConnectedPlayer player) {
    if (this.redis.isShutdown()) {
      player.disconnect(Component.translatable("velocity.kick.shutdown"));
      return false;
    }

    // The player registry allows one connection per player, so an entry of this proxy is stale.
    final PlayerEntry existingEntry = this.depot.get(player.getUniqueId());
    if (existingEntry != null
        && !existingEntry.getProxyId().equalsIgnoreCase(this.redis.getProxyId())) {
      if (this.server.getConfiguration().isKickExistingPlayers()) {
        Component component = Component.translatable("multiplayer.disconnect.duplicate_login");
        this.redis.publish(new VelocityKick(player.getUniqueId(), component, existingEntry.getProxyId()));
      } else {
        Component component = Component.translatable("velocity.error.already-connected-proxy.remote");
        // We're on a depot executor, not the connection's event loop.
        player.getConnection().eventLoop().execute(() -> player.disconnect0(component, true));
        return false;
      }
    }

    this.upsertPlayerEntry(player);
    return true;
  }

  /**
   * Returns whether a newer connection of the player is registered on this proxy.
   */
  private boolean isReplaced(ConnectedPlayer player) {
    final ConnectedPlayer currentPlayer = this.server.getPlayer(player.getUniqueId()).orElse(null);
    return currentPlayer != null && currentPlayer != player;
  }

  /**
   * Removes the player's entry if this proxy owns it.
   */
  private void removeOwnEntry(ConnectedPlayer player) {
    if (this.redis.isShutdown()) {
      return;
    }

    PlayerEntry existing = this.depot.get(player.getUniqueId());
    if (existing == null) {
      return;
    }

    if (!existing.getProxyId().equalsIgnoreCase(this.redis.getProxyId())) {
      return;
    }

    this.depot.remove(player.getUniqueId());
  }

  /**
   * Called when a {@link ConnectedPlayer} switches servers.
   *
   * @param player the player that switched servers
   * @param serverName the name of the server that the player switched to
   */
  public void onPlayerSwitchServer(ConnectedPlayer player, String serverName) {
    if (this.redis.isShutdown()) {
      return;
    }

    PlayerEntry playerEntry = this.getPlayerEntry(player.getUniqueId());
    if (playerEntry == null) {
      return;
    }

    // The player may have moved to another proxy since.
    if (!playerEntry.getProxyId().equalsIgnoreCase(this.redis.getProxyId())) {
      return;
    }

    playerEntry.setServerName(serverName);
    if (this.redis.isShutdown()) {
      return;
    }

    playerEntry.upsert();
  }

  /**
   * Get the total player count across all proxies, currently present in the depot.
   *
   * @return the total player count
   */
  public int getTotalPlayerCount() {
    return this.totalPlayerCount;
  }

  /**
   * Get the number of players on a specific server across all proxies, as of the last player
   * entry sync. Unlike {@link #getPlayerEntriesInServer(String)}, this does not query Redis.
   *
   * @param serverName the name of the server, compared case-insensitively
   * @return the number of players on the server
   */
  public int getPlayerCountInServer(@NotNull String serverName) {
    return this.serverPlayerCounts.getOrDefault(serverName, 0);
  }

  /**
   * Get every player entry across all proxies, as of the last player entry sync. Unlike
   * {@link #getAll()}, this does not query Redis, so it is safe to call on a network thread.
   *
   * @return an unmodifiable list of the player entries read by the last sync; never null
   */
  public @NotNull @Unmodifiable List<PlayerEntry> getSyncedPlayerEntries() {
    return this.syncedPlayerEntries;
  }

  /**
   * Get a player entry by their unique ID.
   *
   * @param uniqueId the unique ID of the player
   * @return the player entry, or {@code null} if the player is not present in the depot
   */
  public @Nullable PlayerEntry getPlayerEntry(UUID uniqueId) {
    return this.depot.get(uniqueId);
  }

  /**
   * Get a player entry by their username.
   *
   * @param username the username of the player
   * @return the player entry, or {@code null} if the player is not present in the depot
   */
  public @Nullable PlayerEntry getPlayerEntry(String username) {
    for (PlayerEntry entry : this.depot.values()) {
      if (entry.getUsername().equalsIgnoreCase(username)) {
        return entry;
      }
    }

    return null;
  }

  /**
   * Checks whether a player is online.
   *
   * @param uniqueId the unique ID of the player
   * @return {@code true} if the player is online, {@code false} otherwise
   */
  public boolean isPlayerOnline(UUID uniqueId) {
    return this.depot.contains(uniqueId);
  }

  /**
   * Checks whether a player is online.
   *
   * @param username the username of the player
   * @return {@code true} if the player is online, {@code false} otherwise
   */
  public boolean isPlayerOnline(String username) {
    for (PlayerEntry entry : this.depot.values()) {
      if (entry.getUsername().equalsIgnoreCase(username)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Retrieves a list of player entries associated with a specific server.
   *
   * @param serverName the name of the server whose player entries are to be retrieved; must not be null
   * @return an unmodifiable list of {@link PlayerEntry} objects representing the players currently on the specified server; never null
   */
  public @NotNull @Unmodifiable List<PlayerEntry> getPlayerEntriesInServer(@NotNull String serverName) {
    return List.copyOf(this.queryAll(playerEntry -> serverName.equalsIgnoreCase(playerEntry.getServerName())));
  }

  /**
   * Retrieves a list of player entries associated with a specific proxy.
   *
   * @param proxyId the identifier of the proxy whose player entries are to be retrieved;
   *                must not be null or empty
   * @return an unmodifiable list of {@link PlayerEntry} objects representing players
   *         currently associated with the specified proxy; never null
   */
  public @NotNull @Unmodifiable List<PlayerEntry> getPlayerEntriesOnProxy(String proxyId) {
    return List.copyOf(this.queryAll(playerEntry -> playerEntry.getProxyId().equalsIgnoreCase(proxyId)));
  }

  /**
   * Upserts a player's entry in the depot. If an entry for the given player already exists,
   * it is updated with the latest details. If it doesn't exist, a new entry is created.
   *
   * @param player the {@link ConnectedPlayer} object representing the player for whom the entry is to be upserted; must not be null
   */
  public void upsertPlayerEntry(@NotNull ConnectedPlayer player) {
    if (this.redis.isShutdown()) {
      return;
    }

    PlayerEntry playerEntry = new PlayerEntry(player, this.redis.getProxyId());
    playerEntry.setDepot(this.depot);

    this.depot.upsert(playerEntry);
  }

  /**
   * Updates the total player count by recalculating the number of entries in the depot.
   */
  private void updateTotalPlayerCount() {
    if (this.redis.isShutdown()) {
      return;
    }

    this.totalPlayerCount = this.depot.size();
  }

  /**
   * Synchronizes the player entries within the depot. This method ensures that the depot's
   * player entries are kept up to date and consistent with the current state of players on
   * the server, and refreshes the per-server player counts from the same read.
   */
  private void syncPlayerEntries() {
    if (this.redis.isShutdown()) {
      return;
    }

    Collection<PlayerEntry> playerEntries = this.depot.values();
    this.serverPlayerCounts = countPlayersByServer(playerEntries);
    this.syncedPlayerEntries = List.copyOf(playerEntries);

    Map<UUID, PlayerEntry> storedPlayers = playerEntries.stream()
        .collect(Collectors.toMap(PlayerEntry::getUniqueId, Function.identity()));

    for (ConnectedPlayer player : this.server.getOnlinePlayers()) {
      if (!player.isFullyConnected()) {
        continue;
      }

      if (!this.needsUpsert(player, storedPlayers.get(player.getUniqueId()))) {
        continue;
      }

      this.upsertPlayerEntry(player);
    }

    for (PlayerEntry playerEntry : playerEntries) {
      if (!playerEntry.getProxyId().equalsIgnoreCase(this.redis.getProxyId())) {
        continue;
      }

      if (this.server.getPlayer(playerEntry.getUniqueId()).isPresent()) {
        continue;
      }

      playerEntry.remove();
    }
  }

  /**
   * Whether the entry for a connected player has to be written again: it is missing, or this
   * proxy's entry no longer says whether the player may be listed in the server list ping. A
   * settings packet does not write to Redis itself, since a client can send one after another and
   * each write would block the network thread, so the change reaches Redis here instead.
   *
   * @param player the connected player
   * @param stored the player's entry as read by this sync, or {@code null} if there is none
   * @return {@code true} if the entry should be written again
   */
  private boolean needsUpsert(ConnectedPlayer player, @Nullable PlayerEntry stored) {
    if (stored == null) {
      return true;
    }

    return stored.getProxyId().equalsIgnoreCase(this.redis.getProxyId())
        && stored.isClientListingAllowed() != player.getPlayerSettings().isClientListingAllowed();
  }

  private static Map<String, Integer> countPlayersByServer(Collection<PlayerEntry> playerEntries) {
    Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (PlayerEntry playerEntry : playerEntries) {
      if (playerEntry.getServerName() != null) {
        counts.merge(playerEntry.getServerName(), 1, Integer::sum);
      }
    }

    return Collections.unmodifiableMap(counts);
  }
}
