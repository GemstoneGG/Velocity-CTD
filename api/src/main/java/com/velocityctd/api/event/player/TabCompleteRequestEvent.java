/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.event.player;

import static com.google.common.base.Preconditions.checkNotNull;

import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.Player;

/**
 * This event is fired after a tab complete request is sent by a player, for clients on
 * Minecraft 1.12.2 and below.
 *
 * <p>Listeners may inspect and modify the partial message before it is sent to the
 * remote server for tab completion.</p>
 */
@AwaitingEvent
public class TabCompleteRequestEvent {

  /**
   * The player who initiated the tab completion request.
   */
  private final Player player;

  /**
   * The message fragment provided by the player that is being completed.
   */
  private String partialMessage;

  /**
   * Constructs a new {@code TabCompleteRequestEvent}.
   *
   * @param player the player who initiated the tab completion request
   * @param partialMessage the message being partially completed
   */
  public TabCompleteRequestEvent(Player player, String partialMessage) {
    this.player = checkNotNull(player, "player");
    this.partialMessage = checkNotNull(partialMessage, "partialMessage");
  }

  /**
   * Returns the player requesting the tab completion.
   *
   * @return the requesting player
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Returns the message being partially completed.
   *
   * @return the partial message
   */
  public String getPartialMessage() {
    return partialMessage;
  }

  /**
   * Updates the partial message to be used for tab completion.
   *
   * @param partialMessage the new partial message string
   */
  public void setPartialMessage(String partialMessage) {
    this.partialMessage = partialMessage;
  }

  /**
   * Returns a string representation of this event, useful for debugging.
   *
   * @return a string with the player and partial message
   */
  @Override
  public String toString() {
    return "TabCompleteRequestEvent{"
        + "player=" + player
        + ", partialMessage='" + partialMessage + '\''
        + '}';
  }
}
