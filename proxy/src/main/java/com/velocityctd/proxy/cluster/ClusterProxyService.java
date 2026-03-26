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

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Provides proxy discovery and cross-proxy operations across the cluster.
 */
public interface ClusterProxyService {

  Collection<String> getAllProxyIds();

  String getSelfProxyId();

  boolean isMultiProxy();

  CompletableFuture<Boolean> reloadProxy(String proxyId);

  CompletableFuture<Long> queryProxyUptime(String proxyId);
}
