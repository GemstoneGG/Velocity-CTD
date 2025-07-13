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
import io.netty.buffer.ByteBuf;

/**
 * The {@code TitleTimesPacket} class represents a packet that handles the timing settings for a title in
 * Minecraft, such as fade-in, stay, and fade-out durations.
 *
 * <p>This packet is used to set the timing properties for a title displayed to the player.</p>
 *
 * <p>It extends the {@link GenericTitlePacket} to inherit basic title properties and adds specific timing
 * controls for the title display.</p>
 */
public class TitleTimesPacket extends GenericTitlePacket {

  /**
   * Number of ticks for the title to fade in.
   */
  private int fadeIn;

  /**
   * Number of ticks the title remains fully visible.
   */
  private int stay;

  /**
   * Number of ticks for the title to fade out.
   */
  private int fadeOut;

  /**
   * Constructs a new {@code TitleTimesPacket} and sets its action type to {@code SET_TIMES}.
   */
  public TitleTimesPacket() {
    setAction(ActionType.SET_TIMES);
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    buf.writeInt(fadeIn);
    buf.writeInt(stay);
    buf.writeInt(fadeOut);
  }

  @Override
  public final int getFadeIn() {
    return fadeIn;
  }

  @Override
  public final void setFadeIn(final int fadeIn) {
    this.fadeIn = fadeIn;
  }

  @Override
  public final int getStay() {
    return stay;
  }

  @Override
  public final void setStay(final int stay) {
    this.stay = stay;
  }

  @Override
  public final int getFadeOut() {
    return fadeOut;
  }

  @Override
  public final void setFadeOut(final int fadeOut) {
    this.fadeOut = fadeOut;
  }

  @Override
  public final String toString() {
    return "TitleTimesPacket{"
        + ", fadeIn=" + fadeIn
        + ", stay=" + stay
        + ", fadeOut=" + fadeOut
        + '}';
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
