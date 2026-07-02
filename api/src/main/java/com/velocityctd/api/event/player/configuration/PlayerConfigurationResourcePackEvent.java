/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.event.player.configuration;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackRequestLike;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Fired while a player is in the configuration state, allowing plugins to apply resource packs
 * before the player enters play.
 *
 * <p>If a resource pack is set, the player is held in configuration until every pack reaches a
 * terminal status. The event is fired on every (re)configuration; use {@link #isFirstJoin()} to
 * tell the initial post-login configuration (e.g. network-wide packs) from a server-switch
 * reconfiguration (e.g. per-server packs).</p>
 *
 * @since Minecraft 1.20.2
 */
@AwaitingEvent
public final class PlayerConfigurationResourcePackEvent {

  private final Player player;
  private final ServerConnection server;
  private final boolean firstJoin;

  private @Nullable ResourcePackRequest resourcePack;

  /**
   * Constructs a new {@link PlayerConfigurationResourcePackEvent}.
   *
   * @param player    the player being configured
   * @param server    the server (re-)configuring the player
   * @param firstJoin whether this is the initial configuration following login
   */
  public PlayerConfigurationResourcePackEvent(Player player,
                                              ServerConnection server,
                                              boolean firstJoin) {
    this.player = Preconditions.checkNotNull(player, "player");
    this.server = server;
    this.firstJoin = firstJoin;
  }

  /**
   * Gets the player being configured.
   *
   * @return the player
   */
  public Player getPlayer() {
    return this.player;
  }

  /**
   * Gets the server (re-)configuring the player.
   *
   * @return the configuring server connection
   */
  public ServerConnection getServer() {
    return this.server;
  }

  /**
   * Gets whether this is the initial configuration following login, rather than a server-switch
   * reconfiguration.
   *
   * @return {@code true} if this is the player's first configuration this session
   */
  public boolean isFirstJoin() {
    return this.firstJoin;
  }

  /**
   * Gets the resource pack request to apply during this configuration, if any.
   *
   * @return the resource pack request, or {@code null} if none has been set
   */
  public @Nullable ResourcePackRequest getResourcePack() {
    return this.resourcePack;
  }

  /**
   * Sets the resource pack(s) to apply during this configuration.
   *
   * <p>Accepts a single {@link com.velocitypowered.api.proxy.player.ResourcePackInfo} or a
   * {@link ResourcePackRequest}; pass {@code null} to apply none.</p>
   *
   * @param resourcePack the resource pack request to apply, or {@code null} to apply none
   */
  public void setResourcePack(@Nullable ResourcePackRequestLike resourcePack) {
    this.resourcePack = resourcePack == null ? null : resourcePack.asResourcePackRequest();
  }

  @Override
  public String toString() {
    return "PlayerConfigurationResourcePackEvent{"
        + "player=" + player
        + ", server=" + server
        + ", firstJoin=" + firstJoin
        + ", resourcePack=" + resourcePack
        + '}';
  }
}
