/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.player;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import java.util.List;

/**
 * Fired when a {@link Player} unregisters one or more plugin channels from the proxy.
 *
 * <p>This event is triggered when the client sends a <em>plugin message</em> through the
 * special <strong>"unregister"</strong> channel, indicating that it will no longer
 * communicate over the specified plugin message channels.</p>
 *
 * <p>Velocity dispatches this event asynchronously and will not wait for event listeners
 * to complete before continuing with normal processing.</p>
 *
 * <p>Note that plugin message registration and unregistration are part of the
 * Minecraft plugin messaging protocol used to coordinate communication between
 * the client, proxy, and backend servers.</p>
 *
 * @since 3.4.0
 */
public final class PlayerChannelUnregisterEvent {

  /**
   * The {@link Player} who sent the unregister message.
   *
   * <p>This represents the client that has requested to remove one or more plugin
   * message channels from its registration list.</p>
   */
  private final Player player;

  /**
   * The list of {@link ChannelIdentifier}s that were unregistered by the player.
   *
   * <p>Each identifier corresponds to a specific plugin message channel that the client
   * has indicated it no longer wishes to use.</p>
   */
  private final List<ChannelIdentifier> channels;

  /**
   * Constructs a new {@link PlayerChannelUnregisterEvent}.
   *
   * @param player the player that sent the unregister message
   * @param channels the list of {@link ChannelIdentifier}s being unregistered
   * @throws NullPointerException if {@code player} or {@code channels} is {@code null}
   */
  public PlayerChannelUnregisterEvent(final Player player, final List<ChannelIdentifier> channels) {
    this.player = Preconditions.checkNotNull(player, "player");
    this.channels = Preconditions.checkNotNull(channels, "channels");
  }

  /**
   * Gets the {@link Player} who sent the unregister message.
   *
   * @return the player involved in this event
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Gets the list of {@link ChannelIdentifier}s that the player has unregistered.
   *
   * <p>These identifiers correspond to the plugin message channels that the client has
   * indicated it will no longer use.</p>
   *
   * @return the list of unregistered channels
   */
  public List<ChannelIdentifier> getChannels() {
    return channels;
  }

  /**
   * Returns a string representation of this event, useful for debugging.
   *
   * @return a string describing the player and unregistered channels
   */
  @Override
  public String toString() {
    return "PlayerChannelUnregisterEvent{"
        + "player=" + player
        + ", channels=" + channels
        + '}';
  }
}
