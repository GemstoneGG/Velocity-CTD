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

package com.velocityctd.proxy.cluster.local;

import com.velocityctd.proxy.cluster.ClusterPlayer;
import com.velocityctd.proxy.cluster.ClusterPlayerService;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Local (single-proxy) implementation of {@link ClusterPlayerService}.
 */
public final class LocalClusterPlayerService implements ClusterPlayerService {

  private final VelocityServer server;

  public LocalClusterPlayerService(final VelocityServer server) {
    this.server = server;
  }

  @Override
  public int getTotalPlayerCount() {
    return this.server.getLocalPlayerCount();
  }

  @Override
  public int getPlayersOnServerCount(final String serverName) {
    return this.server.getServer(serverName)
        .map(rs -> rs.getPlayersConnected().size())
        .orElse(0);
  }

  @Override
  public Collection<ClusterPlayer> getAllPlayers() {
    return this.server.getAllPlayers().stream()
        .<ClusterPlayer>map(this::toLocalPlayer)
        .toList();
  }

  @Override
  public Collection<ClusterPlayer> getPlayersOnServer(final String serverName) {
    return this.server.getServer(serverName)
        .map(rs -> rs.getPlayersConnected().stream()
            .<ClusterPlayer>map(this::toLocalPlayer)
            .toList())
        .orElse(List.of());
  }

  @Override
  public Collection<ClusterPlayer> getPlayersOnProxy(final String proxyId) {
    return getAllPlayers();
  }

  @Override
  public Optional<ClusterPlayer> getPlayer(final String username) {
    return this.server.getPlayer(username).map(this::toLocalPlayer);
  }

  @Override
  public Optional<ClusterPlayer> getPlayer(final UUID uniqueId) {
    return this.server.getPlayer(uniqueId).map(this::toLocalPlayer);
  }

  @Override
  public boolean isPlayerOnline(final String username) {
    return this.server.getPlayer(username).isPresent();
  }

  @Override
  public boolean onPlayerConnect(final ConnectedPlayer player) {
    return true;
  }

  @Override
  public void onPlayerDisconnect(final ConnectedPlayer player) {
  }

  @Override
  public void onPlayerSwitchServer(final ConnectedPlayer player, final String serverName) {
  }

  @Override
  public Collection<String> getPlayerNames() {
    return this.server.getAllPlayers().stream()
        .map(ConnectedPlayer::getUsername)
        .toList();
  }

  @Override
  public void broadcastAlert(final Component message) {
    this.server.sendMessage(message);
  }

  private LocalClusterPlayer toLocalPlayer(ConnectedPlayer player) {
    return new LocalClusterPlayer(this.server, player);
  }
}
