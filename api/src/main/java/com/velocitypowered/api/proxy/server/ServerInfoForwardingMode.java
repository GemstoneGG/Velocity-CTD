/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.proxy.server;

/**
 * Supported per-server player info forwarding methods.
 *
 * <p>These modes define how Velocity forwards player identity and connection information
 * to backend servers. The correct mode must be chosen depending on the backend server
 * version and its support for forwarding mechanisms.</p>
 *
 * @since 3.4.0
 */
public enum ServerInfoForwardingMode {

  /**
   * Inherit the forwarding mode specified globally in the Velocity
   * {@code player-info-forwarding-mode} configuration option.
   */
  FOLLOWUP,

  /**
   * Forward player information using Velocity's modern forwarding system,
   * based on cryptographic signatures. This is the preferred method for
   * modern (1.13+) servers that support it.
   */
  MODERN,

  /**
   * Use BungeeGuard-compatible forwarding. This method provides a
   * shared-secret system for player information forwarding between
   * the proxy and backend servers.
   */
  BUNGEEGUARD,

  /**
   * Use legacy forwarding mode compatible with BungeeCord's
   * IP forwarding. This should only be used when connecting to
   * older (1.12 or below) backend servers that do not support
   * modern forwarding mechanisms.
   */
  LEGACY,

  /**
   * Do not forward any player information. The backend server
   * will see all connections as if they originated directly
   * from the proxy itself.
   */
  NONE
}
