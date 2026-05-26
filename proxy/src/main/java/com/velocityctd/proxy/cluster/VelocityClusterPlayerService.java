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
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Provides player tracking and query operations across the cluster.
 *
 * <p>Exposes more methods for the internal implementation module.
 */
public interface VelocityClusterPlayerService extends ClusterPlayerService {

  @Override
  Collection<VelocityClusterPlayer> getAllPlayers();

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

  void onPlayerDisconnect(ConnectedPlayer player);

  void onPlayerSwitchServer(ConnectedPlayer player, @Nullable String previousServerName, String serverName);

  void onPlayerSettingsChange(ConnectedPlayer player, PlayerSettings settings);
}
