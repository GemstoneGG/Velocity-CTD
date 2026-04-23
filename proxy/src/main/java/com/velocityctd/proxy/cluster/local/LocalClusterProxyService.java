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

package com.velocityctd.proxy.cluster.local;

import static java.util.concurrent.CompletableFuture.completedFuture;

import com.velocityctd.proxy.cluster.VelocityClusterProxyService;
import com.velocitypowered.proxy.VelocityServer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Local (single-proxy) implementation of {@link VelocityClusterProxyService}.
 */
public final class LocalClusterProxyService implements VelocityClusterProxyService {

  private final VelocityServer server;

  public LocalClusterProxyService(VelocityServer server) {
    this.server = server;
  }

  @Override
  public Collection<String> getAllProxyIds() {
    return List.of(getSelfProxyId());
  }

  @Override
  public String getSelfProxyId() {
    return this.server.getProxyId();
  }

  @Override
  public boolean isMultiProxy() {
    return false;
  }

  @Override
  public CompletableFuture<Boolean> reloadProxy(String proxyId) {
    if (!getSelfProxyId().equalsIgnoreCase(proxyId)) {
      return completedFuture(false);
    }
    try {
      return completedFuture(this.server.reloadConfiguration());
    } catch (Exception e) {
      return completedFuture(false);
    }
  }

  @Override
  public CompletableFuture<Long> queryProxyUptime(String proxyId) {
    if (!getSelfProxyId().equalsIgnoreCase(proxyId)) {
      return completedFuture(0L);
    }
    return completedFuture((System.currentTimeMillis() - server.getStartTime()) / 1000);
  }
}
