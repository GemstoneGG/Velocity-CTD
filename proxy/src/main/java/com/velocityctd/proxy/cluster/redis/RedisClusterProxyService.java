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

package com.velocityctd.proxy.cluster.redis;

import com.velocityctd.proxy.cluster.ClusterProxyService;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.impl.transaction.VelocityReload;
import com.velocityctd.proxy.redis.impl.transaction.VelocityUptime;
import com.velocitypowered.api.command.CommandSource;
import java.util.Collection;

/**
 * Redis-backed implementation of {@link ClusterProxyService}.
 */
public final class RedisClusterProxyService implements ClusterProxyService {

  private final VelocityRedis redis;

  public RedisClusterProxyService(final VelocityRedis redis) {
    this.redis = redis;
  }

  @Override
  public Collection<String> getAllProxyIds() {
    return this.redis.getProxyService().getAllProxyIds();
  }

  @Override
  public String getSelfProxyId() {
    return this.redis.getProxyId();
  }

  @Override
  public boolean isMultiProxy() {
    return true;
  }

  @Override
  public void reloadProxy(final CommandSource source, final String proxyId) {
    new VelocityReload(source, proxyId).publish();
  }

  @Override
  public void queryProxyUptime(final CommandSource source, final String proxyId) {
    new VelocityUptime(source, proxyId).publish();
  }
}
