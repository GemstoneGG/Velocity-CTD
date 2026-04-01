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
  public Collection<VelocityClusterPlayer> getAllPlayers() {
    return this.server.getAllPlayers().stream()
        .<VelocityClusterPlayer>map(this::toLocalPlayer)
        .toList();
  }

  @Override
  public Collection<VelocityClusterPlayer> getPlayersOnServer(final String serverName) {
    return this.server.getServer(serverName)
        .map(rs -> rs.getPlayersConnected().stream()
            .<VelocityClusterPlayer>map(this::toLocalPlayer)
            .toList())
        .orElse(List.of());
  }

  @Override
  public Collection<VelocityClusterPlayer> getPlayersOnProxy(final String proxyId) {
    if (!this.server.getProxyId().equalsIgnoreCase(proxyId)) {
      return List.of();
    }
    return getAllPlayers();
  }

  @Override
  public Optional<VelocityClusterPlayer> getPlayer(final String username) {
    return this.server.getPlayer(username).map(this::toLocalPlayer);
  }

  @Override
  public Optional<VelocityClusterPlayer> getPlayer(final UUID uniqueId) {
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
  public void onPlayerSettingsChange(final ConnectedPlayer player, final PlayerSettings settings) {
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
