/*
 * Copyright (C) 2018-2021 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.proxy.server;

import com.google.common.base.Preconditions;
import java.net.InetSocketAddress;

/**
 * ServerInfo represents a server that a player can connect to. This object is immutable and safe
 * for concurrent access.
 */
public record ServerInfo(String name, InetSocketAddress address) implements Comparable<ServerInfo> {

  /**
   * Creates a new ServerInfo object.
   *
   * @param name    the name for the server
   * @param address the address of the server to connect to
   */
  public ServerInfo(String name, InetSocketAddress address) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.address = Preconditions.checkNotNull(address, "address");
  }

  @Override
  public String toString() {
    return "ServerInfo{"
        + "name='" + name + '\''
        + ", address=" + address
        + '}';
  }

  @Override
  public int compareTo(ServerInfo o) {
    return this.name.compareTo(o.name());
  }
}
