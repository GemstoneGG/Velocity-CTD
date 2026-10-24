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
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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
   * How long a shutdown waits for the player calls still queued before it clears the depot.
   */
  private static final long DRAIN_SECONDS = 5L;

  /**
   * How many threads the player calls are spread over. A player always lands on the same one.
   */
  private static final int LANES = 8;

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
   * The threads joins, server switches and quits reach Redis on, so no network thread waits for
   * Redis. A player's calls always go to the same lane, which keeps them in order.
   */
  private final ExecutorService[] playerTasks = createLanes();

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
    for (ExecutorService lane : this.playerTasks) {
      lane.shutdown();
    }

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DRAIN_SECONDS);
    try {
      for (ExecutorService lane : this.playerTasks) {
        long left = deadline - System.nanoTime();
        if (left <= 0 || !lane.awaitTermination(left, TimeUnit.NANOSECONDS)) {
          LOGGER.warn("Player depot calls still running after {} seconds, they are cancelled", DRAIN_SECONDS);
          break;
        }
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }

    // Queued calls return early, since Redis is flagged shut down, so the drain only waits for the
    // calls in flight. One still running is interrupted; a command it already sent runs ahead of
    // the sweep below on the same connection, and every write checks the flag first.
    for (ExecutorService lane : this.playerTasks) {
      lane.shutdownNow();
    }

    try {
      for (ExecutorService lane : this.playerTasks) {
        lane.awaitTermination(1L, TimeUnit.SECONDS);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }

    // Every entry this proxy owns, including those of quits that returned early above.
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
   * {@link #onPlayerConnect(ConnectedPlayer)} on the player depot's thread, so the network thread
   * that logs the player in is free while Redis answers.
   *
   * @param player the player that connected
   * @return completes with whether the player was added to the depot
   */
  public CompletableFuture<Boolean> onPlayerConnectAsync(ConnectedPlayer player) {
    try {
      return CompletableFuture.supplyAsync(() -> this.onPlayerConnect(player), this.laneOf(player.getUniqueId()));
    } catch (RejectedExecutionException exception) {
      // Shutting down: the login cannot be recorded, so it is turned away rather than left waiting.
      player.disconnect(Component.translatable("velocity.kick.shutdown"));
      return CompletableFuture.completedFuture(false);
    }
  }

  /**
   * {@link #onPlayerDisconnect(ConnectedPlayer)} on the player depot's thread.
   *
   * @param player the player that disconnected
   */
  public void onPlayerDisconnectAsync(ConnectedPlayer player) {
    // Asked now, while the caller still holds the player's identity lock. On the depot's thread a
    // reconnect may already be registered, and the old entry would be kept and refuse it.
    if (this.isReplaced(player)) {
      return;
    }

    this.runLater(player, "disconnect", () -> this.removeOwnEntry(player));
  }

  /**
   * Removes the entry written for a login whose connection closed while the duplicate check ran,
   * on the player's lane, unless a newer connection of the player has registered since.
   *
   * @param player the player whose login was abandoned
   */
  public void onPlayerLoginAbortedAsync(ConnectedPlayer player) {
    this.runLater(player, "abandoned login", () -> {
      // The abandoned connection is out of the registry already, so whoever is in it is newer
      // and owns the entry; skipping is harmless, since that login writes the entry itself.
      if (this.server.getPlayer(player.getUniqueId()).isEmpty()) {
        this.removeOwnEntry(player);
      }
    });
  }

  /**
   * {@link #onPlayerSwitchServer(ConnectedPlayer, String)} on the player depot's thread.
   *
   * @param player the player that switched servers
   * @param serverName the name of the server that the player switched to
   */
  public void onPlayerSwitchServerAsync(ConnectedPlayer player, String serverName) {
    this.runLater(player, "server switch", () -> this.onPlayerSwitchServer(player, serverName));
  }

  private void runLater(ConnectedPlayer player, String what, Runnable task) {
    try {
      this.laneOf(player.getUniqueId()).execute(() -> {
        try {
          task.run();
        } catch (RuntimeException exception) {
          LOGGER.error("Could not record the {} of {} in Redis", what, player.getUsername(), exception);
        }
      });
    } catch (RejectedExecutionException exception) {
      // Shutting down: teardown clears this proxy's entries anyway.
    }
  }

  private ExecutorService laneOf(UUID uniqueId) {
    return this.playerTasks[Math.floorMod(uniqueId.hashCode(), this.playerTasks.length)];
  }

  private static ExecutorService[] createLanes() {
    ExecutorService[] lanes = new ExecutorService[LANES];
    for (int i = 0; i < LANES; i++) {
      lanes[i] = Executors.newSingleThreadExecutor(
          Thread.ofPlatform().name("Velocity-CTD - Player Depot #" + i).daemon(true).factory());
    }

    return lanes;
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

    // An entry this proxy owns is left over from an earlier connection: the player registry allows
    // one connection per player here, so it is overwritten rather than refusing the login.
    PlayerEntry existingEntry = this.depot.get(player.getUniqueId());
    if (existingEntry != null && !existingEntry.getProxyId().equalsIgnoreCase(this.redis.getProxyId())) {
      if (this.server.getConfiguration().isKickExistingPlayers()) {
        Component component = Component.translatable("multiplayer.disconnect.duplicate_login");
        this.redis.publish(new VelocityKick(player.getUniqueId(), component, existingEntry.getProxyId()));
      } else {
        Component component = Component.translatable("velocity.error.already-connected-proxy.remote");
        // This runs on a depot lane; the connection is only touched on its event loop.
        player.getConnection().eventLoop().execute(() -> player.disconnect0(component, true));
        return false;
      }
    }

    this.upsertPlayerEntry(player);
    return true;
  }

  /**
   * Whether another connection of the same player is registered on this proxy already, which
   * owns the entry from then on.
   */
  private boolean isReplaced(ConnectedPlayer player) {
    ConnectedPlayer currentPlayer = this.server.getPlayer(player.getUniqueId()).orElse(null);
    return currentPlayer != null && currentPlayer != player;
  }

  /**
   * Removes the player's entry when this proxy owns it.
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

    // A switch that ran late must not rewrite the entry of a proxy the player has moved to since.
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
