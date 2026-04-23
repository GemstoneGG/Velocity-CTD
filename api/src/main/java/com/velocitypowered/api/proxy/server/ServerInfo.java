/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.proxy.server;

import com.google.common.base.Preconditions;
import java.net.InetSocketAddress;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * ServerInfo represents a server that a player can connect to. This object is immutable and safe
 * for concurrent access.
 */
public final class ServerInfo implements Comparable<ServerInfo> {

  /**
   * The name used to identify the server.
   */
  private final String name;

  /**
   * The network address the server is reachable at.
   */
  private final InetSocketAddress address;

  /**
   * The forwarding mode used by the proxy when sending player information
   * to this server.
   */
  @Nullable
  private final PlayerInfoForwarding forwardingMode;

  /**
   * Creates a new ServerInfo object.
   *
   * @param name the name for the server
   * @param address the address of the server to connect to
   * @param forwardingMode the server info forwarding mode, or {@code null} if the mode from the config should be used
   * @since 3.4.0
   */
  public ServerInfo(String name, InetSocketAddress address, @Nullable PlayerInfoForwarding forwardingMode) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.address = Preconditions.checkNotNull(address, "address");
    this.forwardingMode = forwardingMode;
  }

  /**
   * Creates a new ServerInfo object.
   *
   * @param name the name for the server
   * @param address the address of the server to connect to
   */
  public ServerInfo(String name, InetSocketAddress address) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.address = Preconditions.checkNotNull(address, "address");
    this.forwardingMode = null;
  }

  /**
   * Gets the name of the server.
   *
   * @return the name of the server
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the forwarding mode used by the backend server to communicate with Velocity.
   *
   * @return the configured forwarding mode for the server, or {@code null}
   *     if the mode is inherited from the "player-info-forwarding-mode" set in the config
   */
  @Nullable
  public PlayerInfoForwarding getPlayerInfoForwardingMode() {
    return forwardingMode;
  }

  /**
   * Gets the network address of the server.
   *
   * @return the {@link InetSocketAddress} of the server
   */
  public InetSocketAddress getAddress() {
    return address;
  }

  @Override
  public String toString() {
    return "ServerInfo{"
        + "name='" + name + '\''
        + ", address=" + address
        + ", forwarding=" + forwardingMode
        + '}';
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof ServerInfo that)) {
      return false;
    }

    return Objects.equals(name, that.name)
        && Objects.equals(address, that.address);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, address, forwardingMode);
  }

  @Override
  public int compareTo(ServerInfo o) {
    return this.name.compareTo(o.getName());
  }
}
