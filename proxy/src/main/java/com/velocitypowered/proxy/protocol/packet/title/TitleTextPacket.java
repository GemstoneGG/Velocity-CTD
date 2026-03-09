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
 * The {@code TitleTextPacket} class represents a packet that handles the text content for a title
 * displayed to the player in Minecraft.
 *
 * <p>This packet is used to send the main title text to be displayed on the player's screen.</p>
 *
 * <p>It extends the {@link GenericTitlePacket}, inheriting basic title properties and focusing
 * on the specific text content of the title.</p>
 */
public class TitleTextPacket extends GenericTitlePacket {

  /**
   * The text component representing the main title.
   */
  private ComponentHolder component;

  /**
   * Constructs a new {@code TitleTextPacket} with the {@link ActionType#SET_TITLE} preset.
   */
  public TitleTextPacket() {
    setAction(ActionType.SET_TITLE);
  }

  /**
   * Encodes this {@code TitleTextPacket} into the provided {@link ByteBuf}.
   *
   * <p>This writes the serialized title component to the buffer according to the
   * specified protocol version.</p>
   *
   * @param buf the buffer to write to
   * @param direction the packet direction (clientbound or serverbound)
   * @param version the protocol version to use during encoding
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    component.write(buf);
  }

  /**
   * Returns the {@link ComponentHolder} representing the main title content.
   *
   * @return the title component
   */
  @Override
  public ComponentHolder getComponent() {
    return component;
  }

  /**
   * Sets the {@link ComponentHolder} that defines the main title content.
   *
   * @param component the text component to display as the title
   */
  @Override
  public void setComponent(final ComponentHolder component) {
    this.component = component;
  }

  /**
   * Returns a string representation of this {@code TitleTextPacket}.
   *
   * <p>The output includes the component text that will be displayed in the title.</p>
   *
   * @return a human-readable string describing the packet
   */
  @Override
  public String toString() {
    return "TitleTextPacket{"
        + ", component='" + component + '\''
        + '}';
  }

  /**
   * Handles this {@code TitleTextPacket} by delegating it to the session handler.
   *
   * <p>This calls {@link MinecraftSessionHandler#handle(TitleTextPacket)}.</p>
   *
   * @param handler the session handler responsible for processing the packet
   * @return {@code true} if the handler processed the packet successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
