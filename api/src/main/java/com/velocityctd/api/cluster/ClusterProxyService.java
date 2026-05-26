/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.cluster;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Service for discovering proxies and performing cross-proxy operations
 * within the cluster.
 *
 * <p>Provides proxy enumeration, identity queries, and remote management
 * actions such as configuration reload and uptime inspection.</p>
 */
public interface ClusterProxyService {

  /**
   * Gets the identifiers of all proxies currently active in the cluster.
   *
   * @return a collection of proxy identifiers
   */
  Collection<String> getAllProxyIds();

  /**
   * Gets the identifier of this proxy instance.
   *
   * @return this proxy's identifier
   */
  String getSelfProxyId();

  /**
   * Checks whether the cluster consists of more than one proxy.
   *
   * @return {@code true} if multiple proxies are active
   */
  boolean isMultiProxy();

  /**
   * Requests a configuration reload on the specified proxy.
   *
   * @param proxyId the identifier of the target proxy
   * @return a future that completes with {@code true} if the reload succeeded
   */
  CompletableFuture<Boolean> reloadProxy(String proxyId);

  /**
   * Queries the uptime of the specified proxy.
   *
   * @param proxyId the identifier of the target proxy
   * @return a future that completes with the proxy's uptime in seconds
   */
  CompletableFuture<Long> queryProxyUptime(String proxyId);
}
