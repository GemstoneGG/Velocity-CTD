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

package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a status response packet sent from the server to the client.
 */
public class StatusResponsePacket implements MinecraftPacket {

  /**
   * The status message to be sent to the client, usually a JSON string representing server info.
   */
  private @Nullable CharSequence status;

  /**
   * Creates a new, empty {@code StatusResponsePacket}.
   */
  public StatusResponsePacket() {
  }

  /**
   * Creates a new {@code StatusResponsePacket} with the specified status message.
   *
   * @param status the server status message as a {@link CharSequence}
   */
  public StatusResponsePacket(@Nullable final CharSequence status) {
    this.status = status;
  }

  /**
   * Gets the status message from the packet.
   *
   * @return the status message as a {@link String}
   * @throws IllegalStateException if the status is not specified
   */
  public String getStatus() {
    if (status == null) {
      throw new IllegalStateException("Status is not specified");
    }

    return status.toString();
  }

  @Override
  public final String toString() {
    return "StatusResponse{"
        + "status='" + status + '\''
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    status = ProtocolUtils.readString(buf, Short.MAX_VALUE);
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (status == null) {
      throw new IllegalStateException("Status is not specified");
    }

    ProtocolUtils.writeString(buf, status);
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
