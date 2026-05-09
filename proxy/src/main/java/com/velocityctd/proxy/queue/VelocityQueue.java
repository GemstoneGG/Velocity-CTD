/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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
import com.velocityctd.proxy.queue.redis.depot.VelocityQueueDepotEntry;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Local implementation of {@link Queue}.
 */
public class VelocityQueue implements Queue {

  protected final VelocityServer server;
  protected final VelocityQueueManager manager;

  private final VelocityRegisteredServer backend;
  private final QueuePlayerList playerList = new QueuePlayerList();

  private volatile ServerStatus serverStatus;
  private volatile QueueState state;

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
  public @Nullable VelocityQueueEntry getEntry(@NotNull UUID uniqueId) {
    return playerList.get(uniqueId);
  }

  @Override
  public @NotNull @Unmodifiable Collection<VelocityQueueEntry> getEntries() {
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
  public void teardown() {
    playerList.clear();
  }

  public void broadcastMessage(@NotNull Function<VelocityQueueEntry, Component> componentFn) {
    for (VelocityQueueEntry entry : getEntries()) {
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
   * Computes the estimated time to transfer for the given position.
   */
  public int calculateEta(int position) {
    int eta = (int) (server.getConfiguration().getQueue().getSendDelay() * position);
    return Math.max(eta, 0);
  }

  /**
   * Returns the raw internal list. Used by
   * {@link VelocityQueueDepotEntry} for snapshotting.
   */
  @ApiStatus.Internal
  public List<? extends VelocityQueueEntry> getInternalEntries() {
    return playerList.snapshot();
  }

  /**
   * Appends a pre-constructed entry to the end of the deque without priority sorting.
   */
  protected void addEntryInternal(VelocityQueueEntry entry) {
    playerList.addLast(entry);
  }

  /**
   * Creates a new queue entry for the given data.
   *
   * <p>Subclasses override this to produce the appropriate entry type.</p>
   *
   * @param data the player data
   * @return a new entry instance
   */
  protected VelocityQueueEntry createEntry(@NotNull QueueEntryData data) {
    return new VelocityQueueEntry(server, this, data);
  }

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
