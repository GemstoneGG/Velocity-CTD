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

import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.proxy.cluster.ClusterPlayer;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.jetbrains.annotations.Nullable;

/**
 * Local (single-proxy) implementation of {@link ClusterPlayer}.
 */
public final class LocalClusterPlayer implements ClusterPlayer {

  private final VelocityServer server;
  private final ConnectedPlayer player;

  LocalClusterPlayer(final VelocityServer server, final ConnectedPlayer player) {
    this.server = server;
    this.player = player;
  }

  @Override
  public UUID getUniqueId() {
    return player.getUniqueId();
  }

  @Override
  public String getUsername() {
    return player.getUsername();
  }

  @Override
  public @Nullable String getProxyId() {
    return null;
  }

  @Override
  public @Nullable String getServerName() {
    return player.getCurrentServer()
        .map(conn -> conn.getServerInfo().getName()).orElse(null);
  }

  @Override
  public @Nullable String getIpAddress() {
    return player.getRemoteAddress().getAddress().getHostAddress();
  }

  @Override
  public boolean isClientListingAllowed() {
    return player.getPlayerSettings().isClientListingAllowed();
  }

  @Override
  public void kick(final Component reason) {
    player.disconnect0(reason, true);
  }

  @Override
  public void sudo(final String command) {
    if (this.server.getCommandManager().hasCommand(command)) {
      this.server.getCommandManager().executeAsync(player, command);
    } else {
      player.spoofChatInput(command);
    }
  }

  @Override
  public void move(final String targetServer) {
    this.server.getServer(targetServer).ifPresent(
        target -> player.createConnectionRequest(target).fireAndForget());
  }

  @Override
  public void transfer(final CommandSource source, final String ip, final int port) {
    player.transferToHost(new InetSocketAddress(ip, port));
  }

  @Override
  public void sendMessage(final Component message) {
    player.sendMessage(message);
  }

  @Override
  public void queryPing(final CommandSource source) {
    source.sendMessage(Component.translatable("velocity.command.ping.other",
        NamedTextColor.GREEN)
        .arguments(
            Argument.string("player", player.getUsername()),
            Argument.numeric("ping", player.getPing())));
  }

  @Override
  public QueueEntryData toQueueEntryData(final String serverName) {
    return new QueueEntryData(
        player.getUniqueId(),
        player.getUsername(),
        player.getQueuePriority(serverName),
        player.hasPermission("velocity.queue.full.bypass"),
        player.hasPermission("velocity.queue.bypass")
    );
  }

  @Override
  public Optional<ConnectedPlayer> toLocalPlayer() {
    return Optional.of(player);
  }
}
