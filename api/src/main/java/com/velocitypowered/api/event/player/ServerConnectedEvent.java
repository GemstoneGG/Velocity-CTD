/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.player;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This event is fired once the player has successfully connected to the target server and the
 * connection to the previous server has been de-established.
 *
 * <p><strong>Note</strong>: For historical reasons, Velocity does wait on this event to finish
 * firing before continuing the server connection process. This behavior is
 * <strong>deprecated</strong> and likely to be removed in Polymer.</p>
 */
@AwaitingEvent
public final class ServerConnectedEvent {

  /**
   * The player who has connected to the new server.
   */
  private final Player player;

  /**
   * The server the player has successfully connected to.
   */
  private final RegisteredServer server;

  /**
   * The server the player was previously connected to, or {@code null} if none.
   */
  private final @Nullable RegisteredServer previousServer;

  /**
   * Constructs a ServerConnectedEvent.
   *
   * @param player the player that was connected
   * @param server the server the player was connected to
   * @param previousServer the server the player was previously connected to, null if none
   */
  public ServerConnectedEvent(final Player player, final RegisteredServer server,
                              final @Nullable RegisteredServer previousServer) {
    this.player = Preconditions.checkNotNull(player, "player");
    this.server = Preconditions.checkNotNull(server, "server");
    this.previousServer = previousServer;
  }

  /**
   * Returns the player involved in this event.
   *
   * @return the {@link Player} who connected
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Returns the server the player successfully connected to.
   *
   * @return the {@link RegisteredServer} the player connected to
   */
  public RegisteredServer getServer() {
    return server;
  }

  /**
   * Returns the server the player was previously connected to, if any.
   *
   * @return an {@link Optional} of the previous {@link RegisteredServer}, or empty if none
   */
  public Optional<RegisteredServer> getPreviousServer() {
    return Optional.ofNullable(previousServer);
  }

  @Override
  public String toString() {
    return "ServerConnectedEvent{"
        + "player=" + player
        + ", server=" + server
        + ", previousServer=" + previousServer
        + '}';
  }
}
