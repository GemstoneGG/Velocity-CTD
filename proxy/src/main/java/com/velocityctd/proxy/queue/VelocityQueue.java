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

import com.velocityctd.api.queue.Queue;
import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.ServerStatus;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Local implementation of {@link Queue}.
 */
public abstract class VelocityQueue<E extends VelocityQueueEntry> implements Queue {

  protected final VelocityServer server;
  protected final VelocityQueueManager manager;

  private final VelocityRegisteredServer backend;
  private final QueuePlayerList<E> playerList = new QueuePlayerList<>();

  private volatile ServerStatus serverStatus;
  private volatile QueueState state;

  private @Nullable VelocityEtaTracker etaTracker;

  /**
   * Creates a fresh queue for the given backend server.
   */
  public VelocityQueue(VelocityServer server, VelocityQueueManager manager,
                       VelocityRegisteredServer backend, QueueState initialState) {
    this.server = server;
    this.manager = manager;
    this.backend = backend;
    this.serverStatus = ServerStatus.OFFLINE;
    this.state = initialState;
  }

  @Override
  public String getName() {
    return backend.getServerInfo().getName();
  }

  @Override
  public VelocityRegisteredServer getServer() {
    return backend;
  }

  @Override
  public void enqueue(@NotNull Player player) {
    enqueue((ConnectedPlayer) player);
  }

  public void enqueue(ConnectedPlayer player) {
    enqueue(createQueueEntryData(player));
  }

  @Override
  public void enqueue(@NotNull QueueEntryData data) {
    playerList.insertByPriority(createEntry(data));
  }

  @Override
  public void dequeue(@NotNull UUID uniqueId) {
    playerList.remove(uniqueId);
  }

  @Override
  public boolean contains(@NotNull UUID uniqueId) {
    return playerList.contains(uniqueId);
  }

  @Override
  public @Nullable E getEntry(@NotNull UUID uniqueId) {
    return playerList.get(uniqueId);
  }

  @Override
  public @NotNull @Unmodifiable Collection<E> getEntries() {
    return playerList.snapshot();
  }

  @Override
  public Optional<Integer> getPosition(@NotNull UUID uniqueId) {
    return Optional.ofNullable(playerList.get(uniqueId))
        .map(VelocityQueueEntry::getPosition);
  }

  @Override
  public int size() {
    return playerList.size();
  }

  @Override
  public @NotNull ServerStatus getServerStatus() {
    return serverStatus;
  }

  @Override
  public void setServerStatus(@NotNull ServerStatus status) {
    if (this.serverStatus == status) {
      return;
    }
    this.serverStatus = status;
  }

  @Override
  public @NotNull QueueState getState() {
    return state;
  }

  @Override
  public void setState(@NotNull QueueState state) {
    if (this.state == state) {
      return;
    }
    this.state = state;
  }

  @Override
  public synchronized Optional<VelocityEtaTracker> getEtaTracker() {
    if (!manager.isMasterProxy()) {
      etaTracker = null;
      return Optional.empty();
    }

    if (etaTracker == null) {
      etaTracker = new VelocityEtaTracker(server);
    }
    return Optional.of(etaTracker);
  }

  @Override
  public void teardown() {
    playerList.clear();
  }

  public void broadcastMessage(@NotNull Function<E, Component> componentFn) {
    for (E entry : getEntries()) {
      Component msg = componentFn.apply(entry);
      server.getPlayer(entry.getUniqueId()).ifPresent(p -> p.sendMessage(msg));
    }
  }

  /**
   * Initiates the transfer of the given entry to the backend server, unless they are
   * already waiting for a connection.
   */
  @ApiStatus.Internal
  void transferEntry(VelocityQueueEntry entry) {
    if (entry.isWaitingForConnection()) {
      return;
    }
    entry.transfer();
  }

  /**
   * Removes the given entry from the queue without notifying them.
   * Used when the player is no longer online on any proxy.
   */
  @ApiStatus.Internal
  void removeEntry(VelocityQueueEntry entry) {
    playerList.remove(entry.getUniqueId());
  }

  /**
   * Stable-sorts the entries by descending rank, reassigning positions.
   */
  void sortByRankDescending(ToIntFunction<? super VelocityQueueEntry> rank) {
    playerList.sortByRankDescending(rank);
  }

  /**
   * Returns a snapshot of the internal player list.
   */
  @ApiStatus.Internal
  public List<E> getInternalEntries() {
    return playerList.snapshot();
  }

  /**
   * Appends a pre-constructed entry to the end of the deque without priority sorting.
   */
  protected void addEntryInternal(E entry) {
    playerList.addLast(entry);
  }

  /**
   * Creates a new queue entry for the given data. Each concrete subclass binds the entry
   * type via its {@code <E>} parameter and supplies the matching constructor call here.
   *
   * @param data the player data
   */
  protected abstract E createEntry(@NotNull QueueEntryData data);

  /**
   * Creates a {@link QueueEntryData} for the given player joining this queue.
   *
   * @param player the online player
   * @return a populated {@link QueueEntryData}
   */
  private @NotNull QueueEntryData createQueueEntryData(@NotNull ConnectedPlayer player) {
    return new QueueEntryData(
        player.getUniqueId(),
        player.getUsername(),
        player.getQueuePriority(getName()),
        player.hasPermission("velocity.queue.full.bypass"),
        player.hasPermission("velocity.queue.bypass")
    );
  }
}
