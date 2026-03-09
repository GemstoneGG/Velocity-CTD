/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.player;

import static com.google.common.base.Preconditions.checkNotNull;

import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * This event is fired after a tab complete response is sent by the remote server, for clients on
 * 1.12.2 and below. You have the opportunity to modify the response sent to the remote player.
 * Velocity will wait for this event to finish firing before sending the tab complete results to
 * the client. Be sure to be as fast as possible, since the client will freeze while it waits for
 * the tab complete results.
 */
@AwaitingEvent
public class TabCompleteEvent {

  /**
   * The player who requested the tab completion.
   */
  private final Player player;

  /**
   * The partial input that triggered the tab completion request.
   */
  private final String partialMessage;

  /**
   * The list of tab completion suggestions. This list is mutable and may be modified by plugins.
   */
  private final List<String> suggestions;

  /**
   * Constructs a new TabCompleteEvent instance.
   *
   * @param player the player
   * @param partialMessage the partial message
   * @param suggestions the initial list of suggestions
   */
  public TabCompleteEvent(final Player player, final String partialMessage, final List<String> suggestions) {
    this.player = checkNotNull(player, "player");
    this.partialMessage = checkNotNull(partialMessage, "partialMessage");
    this.suggestions = new ArrayList<>(checkNotNull(suggestions, "suggestions"));
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
   * Returns all the suggestions provided to the user, as a mutable list.
   *
   * @return the suggestions
   */
  public List<String> getSuggestions() {
    return suggestions;
  }

  /**
   * Returns a string representation of this {@code TabCompleteEvent}.
   *
   * <p>The output includes the player who requested the tab completion, the partial message that triggered it,
   * and the current list of suggestions (which may have been modified by plugins).</p>
   *
   * @return a human-readable string describing the tab complete event
   */
  @Override
  public String toString() {
    return "TabCompleteEvent{"
        + "player=" + player
        + ", partialMessage='" + partialMessage + '\''
        + ", suggestions=" + suggestions
        + '}';
  }
}
