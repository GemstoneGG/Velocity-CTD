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

package com.velocityctd.proxy.queue.group;

import com.velocityctd.api.queue.LastServerStore;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.provider.RedisProvider;
import java.util.Optional;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link LastServerStore} backed by the shared Redis key/value provider.
 *
 * <p>Group-scoped values use the key {@code <namespace>:queue:last-server:<group>:<uuid>};
 * unscoped legacy values keep {@code <namespace>:queue:last-server:<uuid>}. Records expire
 * after thirty days so stale data does not accumulate indefinitely. Redis failures are
 * logged at debug level and never interrupt queue processing.</p>
 */
public final class RedisLastServerStore implements LastServerStore {

  private static final Logger LOGGER = LogManager.getLogger(RedisLastServerStore.class);
  private static final long EXPIRY_SECONDS = 2_592_000L;
  private static final String KEY_PREFIX = ":queue:last-server:";

  private final VelocityRedis redis;

  /**
   * Creates a store using the given Redis connection holder.
   *
   * @param redis the proxy's Redis instance
   */
  public RedisLastServerStore(@NotNull VelocityRedis redis) {
    this.redis = redis;
  }

  @Override
  public Optional<String> getLastServer(UUID playerId) {
    return read(playerId, null);
  }

  @Override
  public Optional<String> getLastServer(UUID playerId, String groupName) {
    return read(playerId, groupName);
  }

  @Override
  public void recordLastServer(UUID playerId, String serverName) {
    write(playerId, null, serverName);
  }

  @Override
  public void recordLastServer(UUID playerId, String groupName, String serverName) {
    write(playerId, groupName, serverName);
  }

  private Optional<String> read(UUID playerId, @Nullable String groupName) {
    RedisProvider provider = usableProvider();
    if (provider == null) {
      return Optional.empty();
    }

    try {
      return Optional.ofNullable(provider.get(key(provider, playerId, groupName)));
    } catch (Exception exception) {
      LOGGER.debug("Unable to read the last server for {} from Redis.", playerId, exception);
      return Optional.empty();
    }
  }

  private void write(UUID playerId, @Nullable String groupName, String serverName) {
    RedisProvider provider = usableProvider();
    if (provider == null) {
      return;
    }

    try {
      provider.setWithExpiry(key(provider, playerId, groupName), serverName, EXPIRY_SECONDS);
    } catch (Exception exception) {
      LOGGER.debug("Unable to store the last server for {} in Redis.", playerId, exception);
    }
  }

  private RedisProvider usableProvider() {
    if (redis == null || redis.isShutdown()) {
      return null;
    }

    RedisProvider provider = redis.getProvider();
    return provider != null && provider.isConnected() ? provider : null;
  }

  private String key(RedisProvider provider, UUID playerId, @Nullable String groupName) {
    String scope = groupName == null || groupName.isEmpty() ? "" : groupName + ":";
    return provider.getNamespace() + KEY_PREFIX + scope + playerId;
  }
}
