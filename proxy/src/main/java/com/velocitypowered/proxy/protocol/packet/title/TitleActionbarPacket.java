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

  /**
   * Encodes this {@code TitleActionbarPacket} into the provided {@link ByteBuf}.
   *
   * <p>This writes the component content of the action bar directly to the buffer,
   * using the appropriate serialization format based on the protocol version.</p>
   *
   * @param buf the buffer to write the encoded component to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the protocol version used for encoding
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    component.write(buf);
  }

  /**
   * Returns the {@link ComponentHolder} representing the action bar content.
   *
   * @return the action bar text component
   */
  @Override
  public ComponentHolder getComponent() {
    return component;
  }

  /**
   * Sets the {@link ComponentHolder} to be used as the action bar content.
   *
   * @param component the component to display in the action bar
   */
  @Override
  public void setComponent(final ComponentHolder component) {
    this.component = component;
  }

  /**
   * Returns a string representation of this {@code TitleActionbarPacket}.
   *
   * <p>The output includes the action bar's text component.</p>
   *
   * @return a human-readable string describing the packet
   */
  @Override
  public String toString() {
    return "TitleActionbarPacket{"
        + ", component='" + component + '\''
        + '}';
  }

  /**
   * Handles this {@code TitleActionbarPacket} by delegating to the provided session handler.
   *
   * <p>This calls {@link MinecraftSessionHandler#handle(TitleActionbarPacket)}.</p>
   *
   * @param handler the session handler responsible for processing the packet
   * @return {@code true} if the packet was handled successfully; {@code false} otherwise
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
