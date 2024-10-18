/*
 * Copyright (C) 2018-2024 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.redis;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.List;

/**
 * Provides an interface for a manager that handles Redis communications.
 */
public interface RedisManager {

  /**
   * Sends an object to the default Redis channel.
   *
   * @param object the object to be sent to Redis
   */
  void send(Object object);

  /**
   * Sends an object to the specified Redis channel.
   *
   * @param object the object to be sent to Redis
   * @param channel the name of the Redis channel to send the object to
   */
  void send(Object object, String channel);

  /**
   * Saves the given player information to Redis.
   *
   * @param player the player whose information will be saved
   */
  void savePlayer(Player player, RegisteredServer server);

  /**
   * Removes the given player information from Redis.
   *
   * @param player the player whose information will be removed
   */
  void removePlayer(Player player);

  /**
   * Retrieves the number of players connected to a specific server.
   *
   * @param server the name of the server to get the player count for
   * @return the number of players on the specified server
   */
  int getPlayerCount(String server);

  /**
   * Retrieves the total number of players connected to all servers.
   *
   * @return the total player count across all servers
   */
  int getTotalPlayerCount();

  /**
   * Checks whether the RedisManager is enabled.
   *
   * @return {@code true} if RedisManager is enabled, {@code false} otherwise
   */
  boolean isEnabled();

  /**
   * Gets the list of player names currently connected to a specified server.
   *
   * @param name the name of the server to get the connected player names for
   * @return a list of player names connected to the specified server
   */
  List<String> getConnectedPlayerNames(String name);
}
