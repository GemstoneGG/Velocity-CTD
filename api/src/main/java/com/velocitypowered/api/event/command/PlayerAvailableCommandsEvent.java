/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.command;

import static com.google.common.base.Preconditions.checkNotNull;

import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.Player;

/**
 * Allows plugins to modify the packet indicating commands available on the server to a
 * Minecraft 1.13+ client. The given {@link RootCommandNode} is mutable. Velocity will wait
 * for this event to finish firing before sending the list of available commands to the
 * client.
 */
@AwaitingEvent
public class PlayerAvailableCommandsEvent {

  /**
   * The player receiving the list of available commands.
   */
  private final Player player;

  /**
   * The mutable Brigadier root command node representing the available commands.
   */
  private final RootCommandNode<?> rootNode;

  /**
   * Constructs an available commands event.
   *
   * @param player   the targeted player
   * @param rootNode the Brigadier root node
   */
  public PlayerAvailableCommandsEvent(final Player player,
                                      final RootCommandNode<?> rootNode) {
    this.player = checkNotNull(player, "player");
    this.rootNode = checkNotNull(rootNode, "rootNode");
  }

  /**
   * Gets the player that the available commands are being sent to.
   *
   * @return the targeted player
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Gets the root command node that represents the available commands.
   *
   * @return the Brigadier root command node
   */
  public RootCommandNode<?> getRootNode() {
    return rootNode;
  }
}
