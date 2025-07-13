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

  @Override
  public final void setAction(final ActionType action) {
    if (action != ActionType.HIDE && action != ActionType.RESET) {
      throw new IllegalArgumentException("TitleClearPacket only accepts CLEAR and RESET actions");
    }

    super.setAction(action);
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    buf.writeBoolean(getAction() == ActionType.RESET);
  }

  @Override
  public final String toString() {
    return "TitleClearPacket{"
        + ", resetTimes=" + (getAction() == ActionType.RESET)
        + '}';
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
