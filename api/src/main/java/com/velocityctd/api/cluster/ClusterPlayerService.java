/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.cluster;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Service for tracking and querying players across all proxies in the cluster.
 *
 * <p>Provides aggregate counts, lookups by name or UUID, per-server and
 * per-proxy player listings, and cluster-wide broadcast capabilities.</p>
 */
public interface ClusterPlayerService {

  /**
   * Gets the total number of players connected across all proxies.
   *
   * @return the cluster-wide player count
   */
  int getTotalPlayerCount();

  /**
   * Gets the number of players currently on the specified backend server.
   *
   * @param serverName the name of the backend server
   * @return the player count for that server
   */
  int getPlayersOnServerCount(String serverName);

  /**
   * Gets the number of players currently on the specified backend server.
   *
   * @param server the backend server
   * @return the player count for that server
   */
  default int getPlayersOnServerCount(RegisteredServer server) {
    return getPlayersOnServerCount(server.getServerInfo().getName());
  }

  /**
   * Gets all players connected anywhere in the cluster.
   *
   * @return an unmodifiable collection of all cluster players
   */
  Collection<? extends ClusterPlayer> getAllPlayers();

  /**
   * Gets all players currently on the specified backend server.
   *
   * @param serverName the name of the backend server
   * @return an unmodifiable collection of players on that server
   */
  Collection<? extends ClusterPlayer> getPlayersOnServer(String serverName);

  /**
   * Gets all players currently on the specified backend server.
   *
   * @param server the backend server
   * @return an unmodifiable collection of players on that server
   */
  default Collection<? extends ClusterPlayer> getPlayersOnServer(RegisteredServer server) {
    return getPlayersOnServer(server.getServerInfo().getName());
  }

  /**
   * Gets all players connected to the specified proxy.
   *
   * @param proxyId the proxy identifier
   * @return an unmodifiable collection of players on that proxy
   */
  Collection<? extends ClusterPlayer> getPlayersOnProxy(String proxyId);

  /**
   * Looks up a player by username across the cluster.
   *
   * @param username the player's username (case-insensitive)
   * @return an optional containing the player, or empty if not online
   */
  Optional<? extends ClusterPlayer> getPlayer(String username);

  /**
   * Looks up a player by UUID across the cluster.
   *
   * @param uniqueId the player's UUID
   * @return an optional containing the player, or empty if not online
   */
  Optional<? extends ClusterPlayer> getPlayer(UUID uniqueId);

  /**
   * Checks whether a player with the given username is online anywhere in the cluster.
   *
   * @param username the player's username (case-insensitive)
   * @return {@code true} if the player is online
   */
  boolean isPlayerOnline(String username);

  /**
   * Gets the usernames of all players connected across the cluster.
   *
   * @return a collection of player usernames
   */
  Collection<String> getPlayerNames();

  /**
   * Broadcasts an alert message to all players on all proxies in the cluster.
   *
   * @param message the message to broadcast
   */
  void broadcastAlert(Component message);
}
