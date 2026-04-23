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

  private final Set<ConnectedPlayer> viewers = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());

  private final UUID id = UUID.randomUUID();

  private final BossBar bar;

  public static VelocityBossBarImplementation get(BossBar bar) {
    return BossBarImplementation.get(bar, VelocityBossBarImplementation.class);
  }

  VelocityBossBarImplementation(BossBar bar) {
    this.bar = bar;
  }

  public boolean viewerAdd(ConnectedPlayer viewer) {
    if (this.viewers.add(viewer)) {
      ComponentHolder name = new ComponentHolder(viewer.getProtocolVersion(), viewer.translateMessage(this.bar.name()));
      viewer.getBossBarManager().writeUpdate(this, BossBarPacket.createAddPacket(this.id, this.bar, name));
      return true;
    }

    return false;
  }

  public void createDirect(ConnectedPlayer viewer) {
    ComponentHolder name = new ComponentHolder(
        viewer.getProtocolVersion(),
        viewer.translateMessage(this.bar.name()));
    viewer.getConnection().write(BossBarPacket.createAddPacket(this.id, this.bar, name));
  }

  public boolean viewerRemove(ConnectedPlayer viewer) {
    if (this.viewers.remove(viewer)) {
      viewer.getBossBarManager().remove(this, BossBarPacket.createRemovePacket(this.id, this.bar));
      return true;
    }

    return false;
  }

  public void viewerDisconnected(ConnectedPlayer viewer) {
    this.viewers.remove(viewer);
  }

  @Override
  public void bossBarNameChanged(@NotNull BossBar bar, @NotNull Component oldName,
                                 @NotNull Component newName) {
    for (ConnectedPlayer viewer : this.viewers) {
      Component translated = viewer.translateMessage(newName);
      BossBarPacket packet = BossBarPacket.createUpdateNamePacket(this.id, this.bar,
          new ComponentHolder(viewer.getProtocolVersion(), translated));
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }

  @Override
  public void bossBarProgressChanged(@NotNull BossBar bar, float oldProgress,
                                     float newProgress) {
    BossBarPacket packet = BossBarPacket.createUpdateProgressPacket(this.id, this.bar);
    for (ConnectedPlayer viewer : this.viewers) {
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }

  @Override
  public void bossBarColorChanged(@NotNull BossBar bar, BossBar.@NotNull Color oldColor,
                                  BossBar.@NotNull Color newColor) {
    BossBarPacket packet = BossBarPacket.createUpdateStylePacket(this.id, this.bar);
    for (ConnectedPlayer viewer : this.viewers) {
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }

  @Override
  public void bossBarOverlayChanged(@NotNull BossBar bar, BossBar.@NotNull Overlay oldOverlay,
                                    BossBar.@NotNull Overlay newOverlay) {
    BossBarPacket packet = BossBarPacket.createUpdateStylePacket(this.id, this.bar);
    for (ConnectedPlayer viewer : this.viewers) {
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }

  @Override
  public void bossBarFlagsChanged(@NotNull BossBar bar, @NotNull Set<BossBar.Flag> flagsAdded,
                                  @NotNull Set<BossBar.Flag> flagsRemoved) {
    BossBarPacket packet = BossBarPacket.createUpdatePropertiesPacket(this.id, this.bar);
    for (ConnectedPlayer viewer : this.viewers) {
      viewer.getBossBarManager().writeUpdate(this, packet);
    }
  }
}
