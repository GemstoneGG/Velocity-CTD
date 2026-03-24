/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Provides player tracking and query operations across the cluster.
 */
public interface ClusterPlayerService {

  int getTotalPlayerCount();

  int getPlayersOnServerCount(String serverName);

  Collection<ClusterPlayer> getAllPlayers();

  Collection<ClusterPlayer> getPlayersOnServer(String serverName);

  Collection<ClusterPlayer> getPlayersOnProxy(String proxyId);

  Optional<ClusterPlayer> getPlayer(String username);

  Optional<ClusterPlayer> getPlayer(UUID uniqueId);

  boolean isPlayerOnline(String username);

  boolean onPlayerConnect(ConnectedPlayer player);

  void onPlayerDisconnect(ConnectedPlayer player);

  void onPlayerSwitchServer(ConnectedPlayer player, String serverName);

  void onPlayerSettingsChange(ConnectedPlayer player, PlayerSettings settings);

  Collection<String> getPlayerNames();

  void broadcastAlert(Component message);
}
