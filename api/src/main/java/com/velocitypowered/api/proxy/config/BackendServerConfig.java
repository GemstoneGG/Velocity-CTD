/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.proxy.config;

import static java.util.Objects.requireNonNull;

import com.velocitypowered.api.proxy.server.PlayerInfoForwarding;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Exposes server configuration information that plugins may use.
 *
 * <p><b>What's the forwarding mode?</b><br>
 * The server can use a different mode to obtain and forward player info. For instance,
 * if you are running a 1.12 (or lower version) server on a Velocity proxy with MODERN
 * player info forwarding, the server does not support MODERN forwarding. In this case,
 * you must set the forwarding mode for that server to {@link PlayerInfoForwarding#LEGACY},
 * and Velocity will use the legacy mode <em>only</em> for that backend server.
 * Additionally, if the forwarding mode is null it means that the server is using the
 * "player-info-forwarding-mode", set in the configuration.</p>
 *
 * @param address        the hostname or address of the backend server
 * @param forwardingMode the forwarding mode to use when forwarding player information
 *                       to this backend server
 * @since 3.4.0
 * @see PlayerInfoForwarding
 * @see com.velocitypowered.api.proxy.server.ServerInfo#ServerInfo(String, java.net.InetSocketAddress,
 *     PlayerInfoForwarding)
 */
@NullMarked
public record BackendServerConfig(String address, @Nullable PlayerInfoForwarding forwardingMode) {

  /**
   * Creates a new {@link BackendServerConfig}.
   *
   * @param address        the hostname or address of the backend server
   * @param forwardingMode the forwarding mode for this backend server
   * @throws NullPointerException if {@code address} or {@code forwardingMode} is null
   */
  public BackendServerConfig {
    requireNonNull(address);
  }

  /**
   * Creates a new {@link BackendServerConfig} with the given address, using the default.
   *
   * @param address the hostname or address of the backend server
   * @throws NullPointerException if {@code address} is null
   */
  public BackendServerConfig(String address) {
    this(address, null);
  }
}
