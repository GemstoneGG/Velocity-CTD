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

package com.velocityctd.proxy.cluster.redis;

import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.cluster.VelocityClusterPlayerService;
import com.velocityctd.proxy.queue.redis.packet.VelocityBackendLeave;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.data.VelocityAlert;
import com.velocityctd.proxy.redis.depot.player.PlayerDepotService;
import com.velocityctd.proxy.redis.depot.player.PlayerEntry;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Redis-backed implementation of {@link VelocityClusterPlayerService}.
 */
public final class RedisClusterPlayerService implements VelocityClusterPlayerService {

  private final VelocityServer server;
  private final VelocityRedis redis;

  public RedisClusterPlayerService(VelocityServer server, VelocityRedis redis) {
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
  public int getPlayersOnServerCount(String serverName) {
    return playerService().getPlayerEntriesInServer(serverName).size();
  }

  @Override
  public Collection<VelocityClusterPlayer> getAllPlayers() {
    return playerService().getAll().stream()
        .<VelocityClusterPlayer>map(this::toRedisPlayer)
        .toList();
  }

  @Override
  public Collection<VelocityClusterPlayer> getPlayersOnServer(String serverName) {
    return playerService().getPlayerEntriesInServer(serverName).stream()
        .<VelocityClusterPlayer>map(this::toRedisPlayer)
        .toList();
  }

  @Override
  public Collection<VelocityClusterPlayer> getPlayersOnProxy(String proxyId) {
    return playerService().getPlayerEntriesOnProxy(proxyId).stream()
        .<VelocityClusterPlayer>map(this::toRedisPlayer)
        .toList();
  }

  @Override
  public Optional<VelocityClusterPlayer> getPlayer(String username) {
    PlayerEntry entry = playerService().getPlayerEntry(username);
    return Optional.ofNullable(entry).map(this::toRedisPlayer);
  }

  @Override
  public Optional<VelocityClusterPlayer> getPlayer(UUID uniqueId) {
    PlayerEntry entry = playerService().getPlayerEntry(uniqueId);
    return Optional.ofNullable(entry).map(this::toRedisPlayer);
  }

  @Override
  public boolean isPlayerOnline(String username) {
    return playerService().isPlayerOnline(username);
  }

  @Override
  public boolean onPlayerConnect(ConnectedPlayer player) {
    return playerService().onPlayerConnect(player);
  }

  @Override
  public void onPlayerDisconnect(ConnectedPlayer player) {
    playerService().onPlayerDisconnect(player);

    if (server.isQueueEnabled()) {
      // The queue system is currently the only consumer of `VelocityBackendLeave`,
      // hence the `isQueueEnabled()` guard. This may change in the future if we add cluster events!
      VelocityServerConnection connectedServer = player.getConnectedServer();
      if (connectedServer != null) {
        String serverName = connectedServer.getServerInfo().getName();
        server.getRedis().publish(new VelocityBackendLeave(serverName, System.currentTimeMillis()));
      }
    }
  }

  @Override
  public void onPlayerSwitchServer(ConnectedPlayer player, @Nullable String previousServerName, String serverName) {
    playerService().onPlayerSwitchServer(player, serverName);

    if (server.isQueueEnabled() && previousServerName != null) {
      // The queue system is currently the only consumer of `VelocityBackendLeave`,
      // hence the `isQueueEnabled()` guard. This may change in the future if we add cluster events!
      server.getRedis().publish(new VelocityBackendLeave(previousServerName, System.currentTimeMillis()));
    }
  }

  @Override
  public void onPlayerSettingsChange(ConnectedPlayer player, PlayerSettings settings) {
    playerService().onPlayerSettingsChange(player, settings);
  }

  @Override
  public Collection<String> getPlayerNames() {
    return playerService().getAll().stream()
        .map(PlayerEntry::getUsername)
        .toList();
  }

  @Override
  public void broadcastAlert(Component message) {
    redis.publish(new VelocityAlert(message));
  }

  private RedisClusterPlayer toRedisPlayer(PlayerEntry playerEntry) {
    return new RedisClusterPlayer(this.server, playerEntry);
  }
}
