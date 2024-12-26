/*
 * Copyright (C) 2018-2021 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.proxy.server;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.player.PlayerInfo;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.audience.Audience;

/**
 * Represents a server that has been registered with the proxy. The {@code Audience} associated with
 * a {@code RegisteredServer} represent all players on the server connected to this proxy and do not
 * interact with the server in any way.
 */
public interface RegisteredServer extends ChannelMessageSink, Audience {

  /**
   * Returns the {@link ServerInfo} for this server.
   *
   * @return the server info
   */
  ServerInfo getServerInfo();

  /**
   * Returns a list of all the players currently connected to this server on this proxy.
   *
   * @return the players on this proxy
   */
  Collection<Player> getPlayersConnected();

  /**
   * Get the total player count of the server (redis support).
   *
   * @return The total player count.
   */
  long getTotalPlayerCount();

  /**
   * Returns a list of all the players currently connected to this server on all proxies
   * or the current proxy, in case Redis is disabled.
   *
   * @return A list of all player's information.
   */
  List<PlayerInfo> getPlayerInfo();

  /**
   * Attempts to ping the remote server and return the server list ping result.
   *
   * @return the server ping result from the server
   */
  CompletableFuture<ServerPing> ping();

  /**
   * Attempts to ping the remote server and return the server list ping result
   * according to the options provided.
   *
   * @param pingOptions the options provided for pinging the server
   * @return the server ping result from the server
   * @since 3.2.0
   */
  CompletableFuture<ServerPing> ping(PingOptions pingOptions);
}
