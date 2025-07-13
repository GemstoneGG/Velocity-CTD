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

package com.velocitypowered.proxy.protocol.packet.title;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;

/**
 * The {@code TitleActionbarPacket} class represents a packet that handles the content of an action bar
 * displayed to the player in Minecraft.
 *
 * <p>This packet is used to send the text that appears in the action bar, which is a separate text line
 * displayed above the hotbar on the player's screen.</p>
 *
 * <p>It extends the {@link GenericTitlePacket}, inheriting basic title properties and focusing on
 * the content of the action bar.</p>
 */
public class TitleActionbarPacket extends GenericTitlePacket {

  /**
   * The text component that will be displayed in the action bar.
   */
  private ComponentHolder component;

  /**
   * Constructs a new {@code TitleActionbarPacket} and sets its action type.
   */
  public TitleActionbarPacket() {
    setAction(ActionType.SET_TITLE);
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    component.write(buf);
  }

  @Override
  public final ComponentHolder getComponent() {
    return component;
  }

  @Override
  public final void setComponent(final ComponentHolder component) {
    this.component = component;
  }

  @Override
  public final String toString() {
    return "TitleActionbarPacket{"
        + ", component='" + component + '\''
        + '}';
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
