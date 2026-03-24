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

import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.proxy.cluster.ClusterPlayer;
import com.velocityctd.proxy.redis.impl.depot.PlayerEntry;
import com.velocityctd.proxy.redis.impl.packet.VelocityKick;
import com.velocityctd.proxy.redis.impl.packet.VelocityMessage;
import com.velocityctd.proxy.redis.impl.packet.VelocitySudo;
import com.velocityctd.proxy.redis.impl.packet.VelocitySwitchServer;
import com.velocityctd.proxy.redis.impl.transaction.VelocityGetPlayerPing;
import com.velocityctd.proxy.redis.impl.transaction.VelocityTransferRemote;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Redis-backed implementation of {@link ClusterPlayer}.
 */
public final class RedisClusterPlayer implements ClusterPlayer {

  private final VelocityServer server;
  private final PlayerEntry redisEntry;

  RedisClusterPlayer(final VelocityServer server, final PlayerEntry redisEntry) {
    this.server = server;
    this.redisEntry = redisEntry;
  }

  @Override
  public UUID getUniqueId() {
    return redisEntry.getUniqueId();
  }

  @Override
  public String getUsername() {
    return redisEntry.getUsername();
  }

  @Override
  public @Nullable String getProxyId() {
    return redisEntry.getProxyId();
  }

  @Override
  public @Nullable String getServerName() {
    return redisEntry.getServerName();
  }

  @Override
  public @Nullable String getIpAddress() {
    return redisEntry.getIpAddress();
  }

  @Override
  public boolean isClientListingAllowed() {
    return redisEntry.isClientListingAllowed();
  }

  @Override
  public void kick(final Component reason) {
    new VelocityKick(redisEntry.getUniqueId(), reason).publish();
  }

  @Override
  public void sudo(final String command) {
    new VelocitySudo(redisEntry.getUniqueId(), command).publish();
  }

  @Override
  public void move(final String targetServer) {
    new VelocitySwitchServer(redisEntry.getUsername(), targetServer).publish();
  }

  @Override
  public void transfer(final CommandSource source, final String ip, final int port) {
    new VelocityTransferRemote(source, redisEntry.getUniqueId(), redisEntry.getProxyId(), ip, port).publish();
  }

  @Override
  public void sendMessage(final Component message) {
    new VelocityMessage(redisEntry.getUniqueId(), message).publish();
  }

  @Override
  public void queryPing(final CommandSource source) {
    new VelocityGetPlayerPing(source, redisEntry.getUsername()).publish();
  }

  @Override
  public QueueEntryData toQueueEntryData(final String serverName) {
    return new QueueEntryData(
        redisEntry.getUniqueId(),
        redisEntry.getUsername(),
        redisEntry.getQueuePriorities().getOrDefault(serverName, 0),
        redisEntry.isFullServerBypass(),
        redisEntry.isQueueBypass()
    );
  }

  @Override
  public Optional<ConnectedPlayer> toLocalPlayer() {
    return server.getPlayer(redisEntry.getUniqueId());
  }
}
