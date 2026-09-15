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
 * @param address              the hostname or address of the backend server
 * @param forwardingMode       the forwarding mode to use when forwarding player information
 *                             to this backend server
 * @param displayName          the cosmetic name shown to players in place of the server's id,
 *                             or {@code null} to show the id as-is
 * @param customId             an additional identifier players may use in commands to refer to
 *                             this server, or {@code null} for none
 * @param hiddenFromServerList whether to hide this server from player-facing listings and
 *                             tab-completions
 * @since 3.4.0
 * @see PlayerInfoForwarding
 * @see com.velocitypowered.api.proxy.server.ServerInfo#ServerInfo(String, java.net.InetSocketAddress,
 *     PlayerInfoForwarding)
 */
@NullMarked
public record BackendServerConfig(String address, @Nullable PlayerInfoForwarding forwardingMode,
    @Nullable String displayName, @Nullable String customId, boolean hiddenFromServerList) {

  /**
   * Creates a new {@link BackendServerConfig}.
   *
   * @param address              the hostname or address of the backend server
   * @param forwardingMode       the forwarding mode for this backend server
   * @param displayName          the cosmetic name shown to players, or {@code null}
   * @param customId             an additional identifier players may use in commands, or {@code null}
   * @param hiddenFromServerList whether to hide this server from player-facing listings
   * @throws NullPointerException if {@code address} is null
   */
  public BackendServerConfig {
    requireNonNull(address);
  }

  /**
   * Creates a new {@link BackendServerConfig}.
   *
   * @param address        the hostname or address of the backend server
   * @param forwardingMode the forwarding mode for this backend server
   * @throws NullPointerException if {@code address} is null
   */
  public BackendServerConfig(String address, @Nullable PlayerInfoForwarding forwardingMode) {
    this(address, forwardingMode, null, null, false);
  }

  /**
   * Creates a new {@link BackendServerConfig} with the given address, using the default.
   *
   * @param address the hostname or address of the backend server
   * @throws NullPointerException if {@code address} is null
   */
  public BackendServerConfig(String address) {
    this(address, null, null, null, false);
  }
}
