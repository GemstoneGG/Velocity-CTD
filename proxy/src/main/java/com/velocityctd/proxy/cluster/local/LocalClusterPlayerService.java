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

package com.velocityctd.proxy.cluster.local;

import com.google.common.collect.Collections2;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.cluster.VelocityClusterPlayerService;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Local (single-proxy) implementation of {@link VelocityClusterPlayerService}.
 */
public final class LocalClusterPlayerService implements VelocityClusterPlayerService {

  private final VelocityServer server;

  public LocalClusterPlayerService(VelocityServer server) {
    this.server = server;
  }

  @Override
  public int getTotalPlayerCount() {
    return this.server.getLocalPlayerCount();
  }

  @Override
  public int getPlayersOnServerCount(String serverName) {
    return this.server.getServer(serverName)
        .map(rs -> rs.getPlayersConnected().size())
        .orElse(0);
  }

  @Override
  public Collection<VelocityClusterPlayer> getAllPlayers() {
    return Collections2.transform(this.server.getOnlinePlayers(), this::toLocalPlayer);
  }

  @Override
  public Collection<VelocityClusterPlayer> getPlayersOnServer(String serverName) {
    return this.server.getServer(serverName)
        .<Collection<VelocityClusterPlayer>>map(
            rs -> Collections2.transform(rs.getPlayersConnected(), this::toLocalPlayer))
        .orElse(List.of());
  }

  @Override
  public Collection<VelocityClusterPlayer> getPlayersOnProxy(String proxyId) {
    if (!this.server.getProxyId().equalsIgnoreCase(proxyId)) {
      return List.of();
    }
    return this.getAllPlayers();
  }

  @Override
  public Optional<VelocityClusterPlayer> getPlayer(String username) {
    return this.server.getPlayer(username).map(this::toLocalPlayer);
  }

  @Override
  public Optional<VelocityClusterPlayer> getPlayer(UUID uniqueId) {
    return this.server.getPlayer(uniqueId).map(this::toLocalPlayer);
  }

  @Override
  public boolean isPlayerOnline(String username) {
    return this.server.getPlayer(username).isPresent();
  }

  @Override
  public boolean onPlayerConnect(ConnectedPlayer player) {
    return true;
  }

  @Override
  public void onPlayerDisconnect(ConnectedPlayer player) {
  }

  @Override
  public void onPlayerSwitchServer(ConnectedPlayer player, String serverName) {
  }

  @Override
  public void onPlayerSettingsChange(ConnectedPlayer player, PlayerSettings settings) {
  }

  @Override
  public Collection<String> getPlayerNames() {
    return this.server.getOnlinePlayers().stream()
        .map(ConnectedPlayer::getUsername)
        .toList();
  }

  @Override
  public void broadcastAlert(Component message) {
    this.server.sendMessage(message);
  }

  private LocalClusterPlayer toLocalPlayer(ConnectedPlayer player) {
    return new LocalClusterPlayer(this.server, player);
  }
}
