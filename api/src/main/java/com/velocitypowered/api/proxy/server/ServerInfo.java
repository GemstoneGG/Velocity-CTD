/*
 * Copyright (C) 2018-2025 Velocity Contributors
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
  private final ServerInfoForwardingMode forwardingMode;

  /**
   * Creates a new ServerInfo object.
   *
   * @param name the name for the server
   * @param address the address of the server to connect to
   * @param forwardingMode the server info forwarding mode
   * @since 3.4.0
   */
  public ServerInfo(final String name, final InetSocketAddress address, final ServerInfoForwardingMode forwardingMode) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.address = Preconditions.checkNotNull(address, "address");
    this.forwardingMode = Preconditions.checkNotNull(forwardingMode, "forwardingMode");
  }

  /**
   * Creates a new ServerInfo object.
   *
   * @param name the name for the server
   * @param address the address of the server to connect to
   */
  public ServerInfo(final String name, final InetSocketAddress address) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.address = Preconditions.checkNotNull(address, "address");
    this.forwardingMode = ServerInfoForwardingMode.FOLLOWUP;
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
   * Get what mode will the backend server use to communicate with velocity.
   *
   * @return FOLLOWUP mode if the server uses the same mode as set in the main config else one of the available modes
   */
  public ServerInfoForwardingMode getServerInfoForwardingMode() {
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
  public boolean equals(final @Nullable Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof final ServerInfo that)) {
      return false;
    }

    return Objects.equals(name, that.name)
        && Objects.equals(address, that.address)
        && Objects.equals(forwardingMode, that.forwardingMode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, address, forwardingMode);
  }

  @Override
  public int compareTo(final ServerInfo o) {
    return this.name.compareTo(o.getName());
  }
}
