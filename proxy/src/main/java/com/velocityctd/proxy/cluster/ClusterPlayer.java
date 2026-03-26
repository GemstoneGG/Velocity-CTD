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

import com.velocityctd.api.queue.QueueEntryData;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a player in the cluster with both identity and action capabilities.
 */
public interface ClusterPlayer {

  UUID getUniqueId();

  String getUsername();

  @Nullable String getProxyId();

  @Nullable String getServerName();

  @Nullable String getIpAddress();

  boolean isClientListingAllowed();

  void kick(Component reason);

  void sudo(String command);

  void move(String targetServer);

  CompletableFuture<Boolean> transfer(String ip, int port);

  void sendMessage(Component message);

  CompletableFuture<Long> queryPing();

  QueueEntryData toQueueEntryData(String serverName);

  Optional<ConnectedPlayer> toLocalPlayer();
}
