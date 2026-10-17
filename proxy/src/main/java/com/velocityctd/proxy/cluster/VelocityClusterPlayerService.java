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

package com.velocityctd.proxy.cluster;

import com.velocityctd.api.cluster.ClusterPlayerService;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * Provides player tracking and query operations across the cluster.
 *
 * <p>Exposes more methods for the internal implementation module.
 */
public interface VelocityClusterPlayerService extends ClusterPlayerService {

  @Override
  Collection<VelocityClusterPlayer> getAllPlayers();

  /**
   * Gets every player in the cluster as of the last player sync, without a remote lookup, for a
   * caller that answers on a network thread, such as the server list ping.
   *
   * @return the players as of the last sync
   */
  Collection<VelocityClusterPlayer> getPlayersAsOfLastSync();

  /**
   * Gets the names of every player in the cluster as of the last player sync, without a remote
   * lookup, for tab completion, which is asked for on a network thread.
   *
   * @return the player names as of the last sync
   */
  default Collection<String> getPlayerNamesAsOfLastSync() {
    return getPlayersAsOfLastSync().stream()
        .map(VelocityClusterPlayer::getUsername)
        .toList();
  }

  @Override
  Collection<VelocityClusterPlayer> getPlayersOnServer(String serverName);

  @Override
  default Collection<VelocityClusterPlayer> getPlayersOnServer(RegisteredServer server) {
    return getPlayersOnServer(server.getServerInfo().getName());
  }

  @Override
  Collection<VelocityClusterPlayer> getPlayersOnProxy(String proxyId);

  @Override
  Optional<VelocityClusterPlayer> getPlayer(String username);

  @Override
  Optional<VelocityClusterPlayer> getPlayer(UUID uniqueId);

  boolean onPlayerConnect(ConnectedPlayer player);

  /**
   * Registers a player who just logged in, without holding up the calling thread. The login
   * goes on once the future completes with {@code true}; {@code false} means the player was
   * already turned away.
   *
   * @param player the player who logged in
   * @return whether the login may go on
   */
  default CompletableFuture<Boolean> onPlayerConnectAsync(ConnectedPlayer player) {
    return CompletableFuture.completedFuture(onPlayerConnect(player));
  }

  void onPlayerDisconnect(ConnectedPlayer player);

  void onPlayerSwitchServer(ConnectedPlayer player, @Nullable String previousServerName, String serverName);
}
