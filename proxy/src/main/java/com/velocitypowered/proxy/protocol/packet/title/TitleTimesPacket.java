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

  /**
   * Encodes this {@code TitleTimesPacket} into the provided {@link ByteBuf}.
   *
   * <p>This method writes the fade-in, stay, and fade-out durations (in ticks) to the buffer
   * in the order expected by the Minecraft client.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the protocol version used for encoding
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    buf.writeInt(fadeIn);
    buf.writeInt(stay);
    buf.writeInt(fadeOut);
  }

  /**
   * Returns the fade-in duration in ticks.
   *
   * @return the number of ticks the title should take to fade in
   */
  @Override
  public int getFadeIn() {
    return fadeIn;
  }

  /**
   * Sets the fade-in duration in ticks.
   *
   * @param fadeIn the number of ticks to fade in the title
   */
  @Override
  public void setFadeIn(final int fadeIn) {
    this.fadeIn = fadeIn;
  }

  /**
   * Returns the duration in ticks that the title should remain fully visible.
   *
   * @return the stay time in ticks
   */
  @Override
  public int getStay() {
    return stay;
  }

  /**
   * Sets the duration in ticks that the title should remain fully visible.
   *
   * @param stay the number of ticks the title should stay
   */
  @Override
  public void setStay(final int stay) {
    this.stay = stay;
  }

  /**
   * Returns the fade-out duration in ticks.
   *
   * @return the number of ticks the title should take to fade out
   */
  @Override
  public int getFadeOut() {
    return fadeOut;
  }

  /**
   * Sets the fade-out duration in ticks.
   *
   * @param fadeOut the number of ticks to fade out the title
   */
  @Override
  public void setFadeOut(final int fadeOut) {
    this.fadeOut = fadeOut;
  }

  /**
   * Returns a string representation of this {@code TitleTimesPacket}.
   *
   * <p>The output includes the fade-in, stay, and fade-out timing values.</p>
   *
   * @return a human-readable string describing the packet
   */
  @Override
  public String toString() {
    return "TitleTimesPacket{"
        + ", fadeIn=" + fadeIn
        + ", stay=" + stay
        + ", fadeOut=" + fadeOut
        + '}';
  }

  /**
   * Handles this {@code TitleTimesPacket} by delegating it to the session handler.
   *
   * <p>This invokes {@link MinecraftSessionHandler#handle(TitleTimesPacket)}.</p>
   *
   * @param handler the session handler that should process the packet
   * @return {@code true} if the packet was successfully handled
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
