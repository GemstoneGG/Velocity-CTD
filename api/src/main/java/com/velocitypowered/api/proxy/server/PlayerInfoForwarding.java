/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.proxy.server;

/**
 * Supported player info forwarding methods.
 */
public enum PlayerInfoForwarding {

  /**
   * No player information is forwarded. Backend servers will treat all players as offline-mode
   * users with random UUIDs and no skin or IP information.
   *
   * <p>This mode is the safest for servers that do not support Velocity's forwarding,
   * but it disables any functionality dependent on online UUIDs or skins.
   */
  NONE,

  /**
   * Forwards player information using the legacy BungeeCord IP forwarding system.
   * This requires backend servers to have IP forwarding enabled and to use a compatible
   * login handler (such as in Paper or BungeeCord-compatible forks).
   *
   * <p>Use this if your backend servers do not support Velocity’s modern forwarding,
   * but do support BungeeCord-style forwarding.
   */
  LEGACY,

  /**
   * Uses BungeeGuard-style secure IP forwarding, which works by sending a shared secret token
   * in the handshake. This helps secure legacy IP forwarding without modifying the backend server.
   *
   * <p>Requires BungeeGuard (or a compatible plugin) to be installed and configured on backend servers.
   */
  BUNGEEGUARD,

  /**
   * Forwards player information using Velocity's modern forwarding system,
   * based on a signed public key and identity chain.
   *
   * <p>This is the most secure and recommended method, requiring backend support for
   * Velocity’s forwarding protocol (such as in Paper 1.13+ with Velocity forwarding enabled).
   */
  MODERN
}
