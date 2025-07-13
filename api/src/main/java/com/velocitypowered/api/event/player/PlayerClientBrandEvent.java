/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.player;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.Player;

/**
 * Fired when a {@link Player} sends the <code>minecraft:brand</code> plugin message. Velocity will
 * not wait on the result of this event.
 */
public final class PlayerClientBrandEvent {

  /**
   * The player who sent the <code>minecraft:brand</code> plugin message.
   */
  private final Player player;

  /**
   * The brand string reported by the client.
   */
  private final String brand;

  /**
   * Creates a new instance.
   *
   * @param player the {@link Player} of the "sent" client brand
   * @param brand the "sent" client brand
   */
  public PlayerClientBrandEvent(final Player player, final String brand) {
    this.player = Preconditions.checkNotNull(player);
    this.brand = Preconditions.checkNotNull(brand);
  }

  /**
   * Gets the player who sent the client brand.
   *
   * @return the player
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Gets the brand string sent by the client.
   *
   * @return the client brand
   */
  public String getBrand() {
    return brand;
  }

  @Override
  public String toString() {
    return "PlayerClientBrandEvent{"
        + "player=" + player
        + ", brand='" + brand + '\''
        + '}';
  }
}
