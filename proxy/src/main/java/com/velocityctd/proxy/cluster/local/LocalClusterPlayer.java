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

package com.velocityctd.proxy.cluster.local;

import static java.util.concurrent.CompletableFuture.completedFuture;

import com.velocityctd.api.queue.QueueEntryData;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Local (single-proxy) implementation of {@link VelocityClusterPlayer}.
 */
public final class LocalClusterPlayer implements VelocityClusterPlayer {

  private final VelocityServer server;
  private final ConnectedPlayer player;

  LocalClusterPlayer(VelocityServer server, ConnectedPlayer player) {
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
  public String getProxyId() {
    return server.getProxyId();
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
  public void kick(Component reason) {
    player.disconnect0(reason, true);
  }

  @Override
  public void sudo(String message) {
    if (message.startsWith("/")) {
      String fullCommand = message.substring(1);
      String commandLabel = fullCommand.split(" ")[0];
      if (server.getCommandManager().hasCommand(commandLabel)) {
        server.getCommandManager().executeAsync(player, fullCommand);
        return;
      }
    }

    player.spoofChatInput(message);
  }

  @Override
  public void move(String targetServer) {
    this.server.getServer(targetServer).ifPresent(
        target -> player.createConnectionRequest(target).fireAndForget());
  }

  @Override
  public CompletableFuture<Boolean> transfer(String ip, int port) {
    if (player.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      return completedFuture(false);
    }

    CompletableFuture<Boolean> fut = new CompletableFuture<>();
    server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
      player.transferToHost(new InetSocketAddress(ip, port));
      fut.complete(true);
    }).delay(100, TimeUnit.MILLISECONDS).schedule();

    return fut;
  }

  @Override
  public void sendMessage(Component message) {
    player.sendMessage(message);
  }

  @Override
  public CompletableFuture<Long> queryPing() {
    return completedFuture(player.getPing());
  }

  @Override
  public QueueEntryData toQueueEntryData(String serverName) {
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
