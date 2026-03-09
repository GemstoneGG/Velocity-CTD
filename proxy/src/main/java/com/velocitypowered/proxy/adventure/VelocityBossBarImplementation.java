/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

package com.velocitypowered.proxy.adventure;

import com.google.common.collect.MapMaker;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBarImplementation;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of a {@link BossBarImplementation}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class VelocityBossBarImplementation implements BossBar.Listener,
    BossBarImplementation {

  /**
   * The current set of players actively viewing this boss bar.
   *
   * <p>This uses weak keys to allow garbage collection of disconnected players.</p>
   */
  private final Set<ConnectedPlayer> viewers = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());

  /**
   * The unique identifier for this boss bar instance, used in protocol packets.
   */
  private final UUID id = UUID.randomUUID();

  /**
   * The underlying {@link BossBar} instance managed by this implementation.
   */
  private final BossBar bar;

  /**
   * Retrieves the {@link VelocityBossBarImplementation} backing the given {@link BossBar}.
   *
   * <p>This delegates to {@link BossBarImplementation#get(BossBar, Class)} to extract the
   * implementation registered for the provided boss bar.</p>
   *
   * @param bar the {@link BossBar} to unwrap
   * @return the associated {@link VelocityBossBarImplementation} instance
   * @throws IllegalArgumentException if the implementation is not of the expected type
   */
  public static VelocityBossBarImplementation get(final BossBar bar) {
    return BossBarImplementation.get(bar, VelocityBossBarImplementation.class);
  }

  VelocityBossBarImplementation(final BossBar bar) {
    this.bar = bar;
  }

  /**
   * Adds a viewer to the boss bar and sends the appropriate packet to the player.
   *
   * <p>If the viewer is successfully added, this method constructs a {@link ComponentHolder}
   * with the player's protocol version and the translated boss bar name. It then sends a
   * packet to the viewer to display the boss bar.</p>
   *
   * @param viewer the {@link ConnectedPlayer} to add as a viewer of the boss bar
   * @return {@code true} if the viewer was successfully added, {@code false} if the viewer was already added
   */
  public boolean viewerAdd(final ConnectedPlayer viewer) {
    if (this.viewers.add(viewer)) {
      final ComponentHolder name = new ComponentHolder(viewer.getProtocolVersion(), viewer.translateMessage(this.bar.name()));
      viewer.getBossBarManager().writeUpdate(this, BossBarPacket.createAddPacket(this.id, this.bar, name));
      return true;
    }

    return false;
  }

  /**
   * Immediately creates and sends the boss bar to the specified viewer, without
   * checking whether it is already tracked in the viewer set.
   *
   * <p>This method is typically used during server switches to reinitialize
   * boss bars once the client is ready to receive them.</p>
   *
   * @param viewer the {@link ConnectedPlayer} to send the boss bar to
   */
  public void createDirect(final ConnectedPlayer viewer) {
    final ComponentHolder name = new ComponentHolder(
        viewer.getProtocolVersion(),
        viewer.translateMessage(this.bar.name()));
    viewer.getConnection().write(BossBarPacket.createAddPacket(this.id, this.bar, name));
  }

  /**
   * Removes a viewer from the boss bar and sends a packet to hide the boss bar from the player.
   *
   * <p>If the viewer is successfully removed, this method sends a packet to the viewer
   * to remove the boss bar from their view.</p>
   *
   * @param viewer the {@link ConnectedPlayer} to remove as a viewer of the boss bar
   * @return {@code true} if the viewer was successfully removed, {@code false} if the viewer was not present
   */
  public boolean viewerRemove(final ConnectedPlayer viewer) {
    if (this.viewers.remove(viewer)) {
      viewer.getBossBarManager().remove(this, BossBarPacket.createRemovePacket(this.id, this.bar));
      return true;
    }

    return false;
  }

  /**
   * Removes a disconnected player from the list of viewers.
   *
   * <p>This is a passive removal and does not send any packet to the player, assuming
   * the player is already disconnected.</p>
   *
   * @param viewer the {@link ConnectedPlayer} who has disconnected
   */
  public void viewerDisconnected(final ConnectedPlayer viewer) {
    this.viewers.remove(viewer);
  }

  @Override
  public void bossBarNameChanged(final @NotNull BossBar bar, final @NotNull Component oldName,
                                 final @NotNull Component newName) {
    for (final ConnectedPlayer viewer : this.viewers) {
      final Component translated = viewer.translateMessage(newName);
      final BossBarPacket packet = BossBarPacket.createUpdateNamePacket(this.id, this.bar,
          new ComponentHolder(viewer.getProtocolVersion(), translated));
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }

  @Override
  public void bossBarProgressChanged(final @NotNull BossBar bar, final float oldProgress,
                                     final float newProgress) {
    final BossBarPacket packet = BossBarPacket.createUpdateProgressPacket(this.id, this.bar);
    for (final ConnectedPlayer viewer : this.viewers) {
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }

  @Override
  public void bossBarColorChanged(final @NotNull BossBar bar, final BossBar.@NotNull Color oldColor,
                                  final BossBar.@NotNull Color newColor) {
    final BossBarPacket packet = BossBarPacket.createUpdateStylePacket(this.id, this.bar);
    for (final ConnectedPlayer viewer : this.viewers) {
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }

  @Override
  public void bossBarOverlayChanged(final @NotNull BossBar bar, final BossBar.@NotNull Overlay oldOverlay,
                                    final BossBar.@NotNull Overlay newOverlay) {
    final BossBarPacket packet = BossBarPacket.createUpdateStylePacket(this.id, this.bar);
    for (final ConnectedPlayer viewer : this.viewers) {
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }

  @Override
  public void bossBarFlagsChanged(final @NotNull BossBar bar, final @NotNull Set<BossBar.Flag> flagsAdded,
                                  final @NotNull Set<BossBar.Flag> flagsRemoved) {
    final BossBarPacket packet = BossBarPacket.createUpdatePropertiesPacket(this.id, this.bar);
    for (final ConnectedPlayer viewer : this.viewers) {
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }
}
