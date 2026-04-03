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

package com.velocityctd.proxy.cluster.redis;

import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.cluster.VelocityClusterPlayerService;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.data.VelocityAlert;
import com.velocityctd.proxy.redis.depot.player.PlayerDepotService;
import com.velocityctd.proxy.redis.depot.player.PlayerEntry;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Redis-backed implementation of {@link VelocityClusterPlayerService}.
 */
public final class RedisClusterPlayerService implements VelocityClusterPlayerService {

  private final VelocityServer server;
  private final VelocityRedis redis;

  public RedisClusterPlayerService(final VelocityServer server, final VelocityRedis redis) {
    this.server = server;
    this.redis = redis;
  }

  private PlayerDepotService playerService() {
    return this.redis.getPlayerService();
  }

  @Override
  public int getTotalPlayerCount() {
    return playerService().getTotalPlayerCount();
  }

  @Override
  public int getPlayersOnServerCount(final String serverName) {
    return playerService().getPlayerEntriesInServer(serverName).size();
  }

  @Override
  public Collection<VelocityClusterPlayer> getAllPlayers() {
    return playerService().getAll().stream()
        .<VelocityClusterPlayer>map(this::toRedisPlayer)
        .toList();
  }

  @Override
  public Collection<VelocityClusterPlayer> getPlayersOnServer(final String serverName) {
    return playerService().getPlayerEntriesInServer(serverName).stream()
        .<VelocityClusterPlayer>map(this::toRedisPlayer)
        .toList();
  }

  @Override
  public Collection<VelocityClusterPlayer> getPlayersOnProxy(final String proxyId) {
    return playerService().getPlayerEntriesOnProxy(proxyId).stream()
        .<VelocityClusterPlayer>map(this::toRedisPlayer)
        .toList();
  }

  @Override
  public Optional<VelocityClusterPlayer> getPlayer(final String username) {
    final PlayerEntry entry = playerService().getPlayerEntry(username);
    return Optional.ofNullable(entry).map(this::toRedisPlayer);
  }

  @Override
  public Optional<VelocityClusterPlayer> getPlayer(final UUID uniqueId) {
    final PlayerEntry entry = playerService().getPlayerEntry(uniqueId);
    return Optional.ofNullable(entry).map(this::toRedisPlayer);
  }

  @Override
  public boolean isPlayerOnline(final String username) {
    return playerService().isPlayerOnline(username);
  }

  @Override
  public boolean onPlayerConnect(final ConnectedPlayer player) {
    return playerService().onPlayerConnect(player);
  }

  @Override
  public void onPlayerDisconnect(final ConnectedPlayer player) {
    playerService().onPlayerDisconnect(player);
  }

  @Override
  public void onPlayerSwitchServer(final ConnectedPlayer player, final String serverName) {
    playerService().onPlayerSwitchServer(player, serverName);
  }

  @Override
  public void onPlayerSettingsChange(final ConnectedPlayer player, final PlayerSettings settings) {
    playerService().onPlayerSettingsChange(player, settings);
  }

  @Override
  public Collection<String> getPlayerNames() {
    return playerService().getAll().stream()
        .map(PlayerEntry::getUsername)
        .toList();
  }

  @Override
  public void broadcastAlert(final Component message) {
    redis.publish(new VelocityAlert(message));
  }

  private RedisClusterPlayer toRedisPlayer(PlayerEntry playerEntry) {
    return new RedisClusterPlayer(this.server, playerEntry);
  }
}
