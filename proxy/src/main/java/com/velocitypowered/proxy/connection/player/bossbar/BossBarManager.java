/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.connection.player.bossbar;

import com.velocitypowered.proxy.adventure.VelocityBossBarImplementation;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages all boss bar state for a connected player. This manager is responsible for tracking
 * which {@link VelocityBossBarImplementation boss bars} are currently visible to the player,
 * handling when packets should be suppressed, and resending boss bar information when switching
 * servers.
 *
 * <p>Starting with Minecraft 1.20.2, the client clears all boss bars during the login phase.
 * To avoid disconnects caused by sending update packets at the wrong time, this manager can
 * temporarily drop outgoing packets and re-send boss bar state only once it is safe to do so.</p>
 */
public class BossBarManager {

  /**
   * The player that owns this boss bar manager.
   */
  private final ConnectedPlayer player;

  /**
   * The set of boss bars currently associated with this player. These are resent when
   * a server switch occurs.
   */
  private final Set<VelocityBossBarImplementation> bossBars = new HashSet<>();

  /**
   * Whether packets should be dropped instead of sent to the client.
   * This is used during server login/transition.
   */
  private boolean dropPackets = false;

  /**
   * Creates a new {@code BossBarManager} for the given player.
   *
   * @param player the player whose boss bars are being managed
   */
  public BossBarManager(final ConnectedPlayer player) {
    this.player = player;
  }

  /**
   * Records the specified boss bar as active for this player and attempts to send an update packet.
   * If packets are currently being dropped, the bar is still tracked but the packet is not written.
   *
   * @param bar the boss bar being updated
   * @param packet the packet representing the boss bar update
   */
  public synchronized void writeUpdate(final VelocityBossBarImplementation bar, final BossBarPacket packet) {
    this.bossBars.add(bar);
    if (!this.dropPackets) {
      this.player.getConnection().write(packet);
    }
  }

  /**
   * Removes the specified boss bar from tracking and attempts to send a removal packet to the client.
   * If packets are currently being dropped, the removal is tracked locally but no packet is written.
   *
   * @param bar the boss bar being removed
   * @param packet the packet representing the boss bar removal
   */
  public synchronized void remove(final VelocityBossBarImplementation bar, final BossBarPacket packet) {
    this.bossBars.remove(bar);
    if (!this.dropPackets) {
      this.player.getConnection().write(packet);
    }
  }

  /**
   * Re-creates all tracked boss bars for this player. This is typically called after a server
   * switch, once the client is ready to receive boss bar data again. After re-sending, further
   * updates will no longer be dropped.
   */
  public synchronized void sendBossBars() {
    for (VelocityBossBarImplementation bossBar : bossBars) {
      bossBar.createDirect(player);
    }

    this.dropPackets = false;
  }

  /**
   * Marks this manager to temporarily drop all boss bar packets. This should be called when
   * the player is entering a new server to avoid sending packets during the login/configuration
   * phase, which can otherwise disconnect the client.
   */
  public synchronized void dropPackets() {
    this.dropPackets = true;
  }
}
