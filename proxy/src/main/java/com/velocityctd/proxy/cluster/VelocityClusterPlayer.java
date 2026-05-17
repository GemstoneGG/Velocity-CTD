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

package com.velocityctd.proxy.cluster;

import com.velocityctd.api.cluster.ClusterPlayer;
import com.velocityctd.api.queue.QueueEntryData;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Optional;

/**
 * Represents a player in the cluster with both identity and action capabilities.
 *
 * <p>Exposes more methods for the internal implementation module.
 */
public interface VelocityClusterPlayer extends ClusterPlayer {

  @Override
  Optional<ConnectedPlayer> toLocalPlayer();

  QueueEntryData toQueueEntryData(String serverName);
}
