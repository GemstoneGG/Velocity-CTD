/*
 * Copyright (C) 2018-2024 Velocity Contributors
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

package com.velocitypowered.proxy.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

/**
 * The {@code VelocityRedisConfiguration} class holds configuration settings for connecting and managing
 * Redis within the Velocity proxy.
 * <p>
 * This configuration manages Redis connection details such as host, port, authentication, SSL, and other
 * Redis-related settings. It also handles options related to proxy management, like proxy IDs and master
 * proxy configurations.
 * </p>
 */
public record VelocityRedisConfiguration(boolean useRedis, String host, int port, String username, String password,
    boolean useSsl, int maximumRedisConnections, String redisId, String proxyId, String masterProxyId,
    boolean proxyEnabled, boolean proxyIdsEnabled, boolean plistEnabled) {

  /**
   * Attempts to validate the configuration.
   *
   * @return {@code true} if the configuration is sound, {@code false} if not
   */
  public static VelocityRedisConfiguration read(Path path) throws IOException {
    URL defaultConfigLocation = VelocityConfiguration.class.getClassLoader()
        .getResource("default-velocity-redis.toml");

    if (defaultConfigLocation == null) {
      throw new RuntimeException("Default configuration file does not exist.");
    }

    try (final CommentedFileConfig config = CommentedFileConfig.builder(path)
        .defaultData(defaultConfigLocation)
        .autosave()
        .preserveInsertionOrder()
        .sync()
        .build()
    ) {
      config.load();

      final CommentedConfig commandsConfig = config.get("commands");

      final boolean useRedis = config.get("use-redis");
      final String host = config.get("host");
      final int port = config.get("port");

      // Allow username to be null, to automatically make sure redis doesn't try to add credentials for it
      final String usernameTemp = config.get("username");
      final String username = usernameTemp.equalsIgnoreCase("") ? null : usernameTemp;

      final String password = config.get("password");
      final boolean useSsl = config.get("use-ssl");
      final int maximumRedisConnections = config.get("maximum-redis-connections");
      final String redisId = config.get("redis-id");
      final String proxyId = config.get("proxy-id");
      final String masterProxyId = config.get("master-proxy-id");

      final boolean proxyEnabled = commandsConfig.get("proxy-enabled");
      final boolean proxyIdsEnabled = commandsConfig.get("proxyids-enabled");
      final boolean plistEnabled = commandsConfig.get("plist-enabled");

      return new VelocityRedisConfiguration(useRedis, host, port, username, password, useSsl, maximumRedisConnections,
          redisId, proxyId, masterProxyId, proxyEnabled, proxyIdsEnabled, plistEnabled);
    }
  }
}
