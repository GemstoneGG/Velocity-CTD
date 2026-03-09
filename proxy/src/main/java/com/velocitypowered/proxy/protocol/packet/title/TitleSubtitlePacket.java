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
 * The {@code TitleSubtitlePacket} class represents a packet that handles the subtitle content for a title
 * displayed to the player in Minecraft.
 *
 * <p>This packet is used to send the subtitle text that appears below the main title on the player's screen.</p>
 *
 * <p>It extends the {@link GenericTitlePacket}, inheriting basic title properties and focusing
 * on the subtitle content of the title.</p>
 */
public class TitleSubtitlePacket extends GenericTitlePacket {

  /**
   * The subtitle component to display.
   */
  private ComponentHolder component;

  /**
   * Constructs a new {@code TitleSubtitlePacket} and sets its action type.
   */
  public TitleSubtitlePacket() {
    setAction(ActionType.SET_SUBTITLE);
  }

  /**
   * Encodes this {@code TitleSubtitlePacket} into the provided {@link ByteBuf}.
   *
   * <p>This writes the subtitle component to the buffer using the appropriate format for the given protocol version.</p>
   *
   * @param buf the byte buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the protocol version used for encoding
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    component.write(buf);
  }

  /**
   * Returns the {@link ComponentHolder} representing the subtitle.
   *
   * @return the subtitle text component
   */
  @Override
  public ComponentHolder getComponent() {
    return component;
  }

  /**
   * Sets the {@link ComponentHolder} that contains the subtitle text.
   *
   * @param component the subtitle text component to display
   */
  @Override
  public void setComponent(final ComponentHolder component) {
    this.component = component;
  }

  /**
   * Returns a string representation of this {@code TitleSubtitlePacket}.
   *
   * <p>The output includes the subtitle component.</p>
   *
   * @return a human-readable description of the packet
   */
  @Override
  public String toString() {
    return "TitleSubtitlePacket{"
        + ", component='" + component + '\''
        + '}';
  }

  /**
   * Handles this {@code TitleSubtitlePacket} by passing it to the provided session handler.
   *
   * <p>This delegates to {@link MinecraftSessionHandler#handle(TitleSubtitlePacket)}.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the handler successfully processed the packet
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
