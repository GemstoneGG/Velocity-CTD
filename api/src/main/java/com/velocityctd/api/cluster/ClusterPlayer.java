/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.cluster;

import com.velocitypowered.api.proxy.Player;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a player connected somewhere in the proxy cluster.
 *
 * <p>Provides read-only identity information (UUID, username, proxy, server)
 * and remote actions (kick, message, move, etc.) that are dispatched to
 * whichever proxy currently owns the player's session.</p>
 */
public interface ClusterPlayer {

  /**
   * Gets the player's unique identifier.
   *
   * @return the player's UUID
   */
  UUID getUniqueId();

  /**
   * Gets the player's username.
   *
   * @return the player's username
   */
  String getUsername();

  /**
   * Gets the identifier of the proxy this player is connected to.
   *
   * @return the proxy identifier
   */
  String getProxyId();

  /**
   * Gets the name of the backend server this player is currently on.
   *
   * @return the server name, or {@code null} if the player has not yet been sent to a server
   */
  @Nullable String getServerName();

  /**
   * Gets the player's IP address.
   *
   * @return the IP address string, or {@code null} if unavailable
   */
  @Nullable String getIpAddress();

  /**
   * Checks whether this player allows themselves to be listed in the tab list
   * for other players.
   *
   * @return {@code true} if the player allows client listing
   */
  boolean isClientListingAllowed();

  /**
   * Gets the timestamp, in milliseconds since the epoch, at which the player connected
   * to the proxy they are currently on.
   *
   * @return the join timestamp in milliseconds since the epoch
   */
  long getJoinedAt();

  /**
   * Kicks the player from the proxy with the given reason.
   *
   * @param reason the kick reason displayed to the player
   */
  void kick(Component reason);

  /**
   * Forces the player to execute a command or send a chat message.
   *
   * @param command the command (with or without leading {@code /}) or chat message
   */
  void sudo(String command);

  /**
   * Moves the player to the specified backend server.
   *
   * @param targetServer the name of the target server
   */
  void move(String targetServer);

  /**
   * Transfers the player to a different proxy at the given address.
   *
   * @param ip   the target proxy's IP address
   * @param port the target proxy's port
   * @return a future that completes with {@code true} if the transfer was initiated
   */
  CompletableFuture<Boolean> transfer(String ip, int port);

  /**
   * Sends a chat message to the player.
   *
   * @param message the message to send
   */
  void sendMessage(Component message);

  /**
   * Queries the player's current ping latency.
   *
   * @return a future that completes with the player's ping in milliseconds
   */
  CompletableFuture<Long> queryPing();

  /**
   * Attempts to resolve this cluster player to a local {@link Player} instance
   * on the current proxy.
   *
   * @return an optional containing the local player, or empty if the player
   *         is on a different proxy
   */
  Optional<? extends Player> toLocalPlayer();
}
