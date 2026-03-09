/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.player;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This event is fired when a player types in a chat message. Velocity will wait on this event
 * to finish firing before forwarding it to the server, if the result allows it.
 */
@AwaitingEvent
public final class PlayerChatEvent implements ResultedEvent<PlayerChatEvent.ChatResult> {

  /**
   * The player who sent the chat message.
   */
  private final Player player;

  /**
   * The raw chat message sent by the player.
   */
  private final String message;

  /**
   * The result determining whether the message should be forwarded to the server.
   */
  private ChatResult result;

  /**
   * Constructs a PlayerChatEvent.
   *
   * @param player the player sending the message
   * @param message the message being sent
   */
  public PlayerChatEvent(final Player player, final String message) {
    this.player = Preconditions.checkNotNull(player, "player");
    this.message = Preconditions.checkNotNull(message, "message");
    this.result = ChatResult.allowed();
  }

  /**
   * Gets the player who sent the chat message.
   *
   * @return the player who sent the message
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Gets the raw chat message the player sent.
   *
   * @return the original chat message
   */
  public String getMessage() {
    return message;
  }

  @Override
  public ChatResult getResult() {
    return result;
  }

  /**
   * Set result for the event.
   *
   * @param result the result of event
   * @deprecated for 1.19.1 and newer, set this as denied will kick users
   */
  @Deprecated
  @Override
  public void setResult(final ChatResult result) {
    this.result = Preconditions.checkNotNull(result, "result");
  }

  @Override
  public String toString() {
    return "PlayerChatEvent{"
        + "player=" + player
        + ", message=" + message
        + ", result=" + result
        + '}';
  }

  /**
   * Represents the result of the {@link PlayerChatEvent}.
   */
  public static final class ChatResult implements ResultedEvent.Result {

    /**
     * A result allowing the chat message to be forwarded to the server unchanged.
     */
    private static final ChatResult ALLOWED = new ChatResult(true, null);

    /**
     * A result preventing the chat message from being forwarded.
     */
    private static final ChatResult DENIED = new ChatResult(false, null);

    /**
     * The message to send instead of the original, or {@code null} to use the original message.
     */
    private final @Nullable String message;

    /**
     * Whether the chat message is allowed to be forwarded.
     */
    private final boolean status;

    private ChatResult(final boolean status, final @Nullable String message) {
      this.status = status;
      this.message = message;
    }

    /**
     * Gets the (possibly modified) chat message to be sent.
     *
     * @return an {@link Optional} containing the message, or empty if none
     */
    public Optional<String> getMessage() {
      return Optional.ofNullable(message);
    }

    @Override
    public boolean isAllowed() {
      return status;
    }

    @Override
    public String toString() {
      return status ? "allowed" : "denied";
    }

    /**
     * Allows the message to be sent, without modification.
     *
     * @return the allowed result
     */
    public static ChatResult allowed() {
      return ALLOWED;
    }

    /**
     * Prevents the message from being sent.
     *
     * @return the denied result
     */
    public static ChatResult denied() {
      return DENIED;
    }

    /**
     * Allows the message to be sent, but silently replaces it with another.
     *
     * @param message the message to use instead
     * @return a result with a new message
     */
    public static ChatResult message(final @NonNull String message) {
      Preconditions.checkNotNull(message, "message");
      return new ChatResult(true, message);
    }
  }
}
