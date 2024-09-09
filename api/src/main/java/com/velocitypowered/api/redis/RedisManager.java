/*
 * Copyright (C) 2018-2022 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.redis;

import com.velocitypowered.api.proxy.Player;
import java.util.List;

/**
 * Provides an interface for a manager that handles redis communications.
 */
public interface RedisManager {
  void send(Object object);

  void send(Object object, String channel);

  void savePlayer(Player player);

  void removePlayer(Player player);

  int getPlayerCount(String server);

  int getTotalPlayerCount();

  boolean isEnabled();

  List<String> getConnectedPlayerNames(String name);
}
