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
 * The {@code TitleClearPacket} class represents a packet that handles the clearing or removal of a title
 * from the player's screen in Minecraft.
 *
 * <p>This packet is used to instruct the client to clear any currently displayed title and subtitle.</p>
 *
 * <p>It extends the {@link GenericTitlePacket}, inheriting basic title properties but is specifically
 * focused on clearing the title display.</p>
 */
public class TitleClearPacket extends GenericTitlePacket {

  /**
   * Constructs a {@code TitleClearPacket} with the default action {@link ActionType#HIDE}.
   */
  public TitleClearPacket() {
    setAction(ActionType.HIDE);
  }

  /**
   * Sets the {@link ActionType} for this packet.
   *
   * <p>This packet only allows {@link ActionType#HIDE} and {@link ActionType#RESET}. Attempting
   * to set any other action will throw an {@link IllegalArgumentException}.</p>
   *
   * @param action the action type to set
   * @throws IllegalArgumentException if an invalid action is provided
   */
  @Override
  public void setAction(final ActionType action) {
    if (action != ActionType.HIDE && action != ActionType.RESET) {
      throw new IllegalArgumentException("TitleClearPacket only accepts CLEAR and RESET actions");
    }

    super.setAction(action);
  }

  /**
   * Encodes this {@code TitleClearPacket} into the provided {@link ByteBuf}.
   *
   * <p>Writes a single {@code boolean} to the buffer, where {@code true} indicates
   * a {@link ActionType#RESET} action (which clears and resets fade timings),
   * and {@code false} represents {@link ActionType#HIDE} (which only hides the title).</p>
   *
   * @param buf the buffer to write to
   * @param direction the packet direction (clientbound or serverbound)
   * @param version the protocol version used for encoding
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    buf.writeBoolean(getAction() == ActionType.RESET);
  }

  /**
   * Returns a string representation of this {@code TitleClearPacket}.
   *
   * <p>The output includes whether the action is {@link ActionType#RESET} (which also resets title timings).</p>
   *
   * @return a human-readable description of the packet
   */
  @Override
  public String toString() {
    return "TitleClearPacket{"
        + ", resetTimes=" + (getAction() == ActionType.RESET)
        + '}';
  }

  /**
   * Handles this {@code TitleClearPacket} by passing it to the provided session handler.
   *
   * <p>This delegates to {@link MinecraftSessionHandler#handle(TitleClearPacket)}.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the handler accepted and processed the packet
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
