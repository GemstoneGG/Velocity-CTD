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
   * A cosmetic name shown to players in place of {@link #name} wherever the server is
   * displayed. {@code null} means the server's {@link #name} is shown as-is.
   */
  @Nullable
  private final String displayName;

  /**
   * An additional identifier players may use in commands (such as {@code /server}) to refer to
   * this server, in addition to its {@link #name}. {@code null} means no alias is configured.
   */
  @Nullable
  private final String customId;

  /**
   * Whether this server should be hidden from player-facing server listings and tab-completions.
   * Hidden servers remain fully functional for queues, admin commands, and direct connections.
   */
  private final boolean hiddenFromServerList;

  /**
   * Creates a new ServerInfo object.
   *
   * @param name the name for the server
   * @param address the address of the server to connect to
   * @param forwardingMode the server info forwarding mode, or {@code null} if the mode from the config should be used
   * @param displayName the cosmetic name shown to players, or {@code null} to display {@code name}
   * @param customId an additional identifier players may use in commands, or {@code null} for none
   * @param hiddenFromServerList whether to hide this server from player-facing listings and tab-completions
   * @since 3.4.0
   */
  public ServerInfo(String name, InetSocketAddress address, @Nullable PlayerInfoForwarding forwardingMode,
      @Nullable String displayName, @Nullable String customId, boolean hiddenFromServerList) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.address = Preconditions.checkNotNull(address, "address");
    this.forwardingMode = forwardingMode;
    this.displayName = displayName;
    this.customId = customId;
    this.hiddenFromServerList = hiddenFromServerList;
  }

  /**
   * Creates a new ServerInfo object.
   *
   * @param name the name for the server
   * @param address the address of the server to connect to
   * @param forwardingMode the server info forwarding mode, or {@code null} if the mode from the config should be used
   * @since 3.4.0
   */
  public ServerInfo(String name, InetSocketAddress address, @Nullable PlayerInfoForwarding forwardingMode) {
    this(name, address, forwardingMode, null, null, false);
  }

  /**
   * Creates a new ServerInfo object.
   *
   * @param name the name for the server
   * @param address the address of the server to connect to
   */
  public ServerInfo(String name, InetSocketAddress address) {
    this(name, address, null, null, null, false);
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
   * Gets the cosmetic name that should be shown to players in place of {@link #getName()}.
   *
   * <p>If no display name is configured, this returns {@link #getName()} so callers can always
   * use this method when rendering a server name for players.</p>
   *
   * @return the display name, never {@code null}
   */
  public String getDisplayName() {
    return displayName != null ? displayName : name;
  }

  /**
   * Gets the additional identifier players may use in commands to refer to this server, if any.
   *
   * @return the custom identifier, or {@code null} if none is configured
   */
  @Nullable
  public String getCustomId() {
    return customId;
  }

  /**
   * Returns whether this server should be hidden from player-facing server listings and
   * tab-completions. Hidden servers remain reachable by name and usable by queues and admin
   * commands.
   *
   * @return {@code true} if the server should be hidden from player-facing listings
   */
  public boolean isHiddenFromServerList() {
    return hiddenFromServerList;
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
        + ", displayName='" + displayName + '\''
        + ", customId='" + customId + '\''
        + ", hiddenFromServerList=" + hiddenFromServerList
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
        && Objects.equals(address, that.address)
        && Objects.equals(forwardingMode, that.forwardingMode);
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
