/*
 * Copyright (C) 2020-2023 Velocity Contributors
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
@SuppressWarnings("MissingJavadocMethod")
public final class VelocityBossBarImplementation implements BossBar.Listener,
    BossBarImplementation {
  private final Set<ConnectedPlayer> viewers = Collections.newSetFromMap(
      new MapMaker().weakKeys().makeMap());
  private final UUID id = UUID.randomUUID();
  private final BossBar bar;

  public static VelocityBossBarImplementation get(final BossBar bar) {
    return BossBarImplementation.get(bar, VelocityBossBarImplementation.class);
  }

  VelocityBossBarImplementation(final BossBar bar) {
    this.bar = bar;
  }

  public boolean viewerAdd(final ConnectedPlayer viewer) {
    if (this.viewers.add(viewer)) {
      final ComponentHolder name = new ComponentHolder(
          viewer.getProtocolVersion(),
          viewer.translateMessage(this.bar.name())
      );
      viewer.getConnection().write(BossBarPacket.createAddPacket(this.id, this.bar, name));
      return true;
    }
    return false;
  }

  public boolean viewerRemove(final ConnectedPlayer viewer) {
    if (this.viewers.remove(viewer)) {
      viewer.getConnection().write(BossBarPacket.createRemovePacket(this.id, this.bar));
      return true;
    }
    return false;
  }

  public void viewerDisconnected(final ConnectedPlayer viewer) {
    this.viewers.remove(viewer);
  }

  @Override
  public void bossBarNameChanged(
      final @NotNull BossBar bar,
      final @NotNull Component oldName,
      final @NotNull Component newName
  ) {
    for (final ConnectedPlayer viewer : this.viewers) {
      final Component translated = viewer.translateMessage(newName);
      final BossBarPacket packet = BossBarPacket.createUpdateNamePacket(
          this.id,
          this.bar,
          new ComponentHolder(viewer.getProtocolVersion(), translated)
      );
      viewer.getConnection().write(packet);
    }
  }

  @Override
  public void bossBarProgressChanged(
      final @NotNull BossBar bar,
      final float oldProgress,
      final float newProgress
  ) {
    final BossBarPacket packet = BossBarPacket.createUpdateProgressPacket(this.id, this.bar);
    for (final ConnectedPlayer viewer : this.viewers) {
      viewer.getConnection().write(packet);
    }
  }

  @Override
  public void bossBarColorChanged(
      final @NotNull BossBar bar,
      final BossBar.@NotNull Color oldColor,
      final BossBar.@NotNull Color newColor
  ) {
    final BossBarPacket packet = BossBarPacket.createUpdateStylePacket(this.id, this.bar);
    for (final ConnectedPlayer viewer : this.viewers) {
      viewer.getConnection().write(packet);
    }
  }

  @Override
  public void bossBarOverlayChanged(
      final @NotNull BossBar bar,
      final BossBar.@NotNull Overlay oldOverlay,
      final BossBar.@NotNull Overlay newOverlay
  ) {
    final BossBarPacket packet = BossBarPacket.createUpdateStylePacket(this.id, this.bar);
    for (final ConnectedPlayer viewer : this.viewers) {
      viewer.getConnection().write(packet);
    }
  }

  @Override
  public void bossBarFlagsChanged(
      final @NotNull BossBar bar,
      final @NotNull Set<BossBar.Flag> flagsAdded,
      final @NotNull Set<BossBar.Flag> flagsRemoved
  ) {
    final BossBarPacket packet = BossBarPacket.createUpdatePropertiesPacket(this.id, this.bar);
    for (final ConnectedPlayer viewer : this.viewers) {
      viewer.getConnection().write(packet);
    }
  }
}
