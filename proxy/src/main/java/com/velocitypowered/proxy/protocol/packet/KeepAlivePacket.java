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

/**
 * Represents a KeepAlive packet in Minecraft. This packet is used to ensure that the connection
 * between the client and the server shall still be active by sending a randomly generated ID that
 * the client must respond to.
 */
public class KeepAlivePacket implements MinecraftPacket {

  /**
   * The randomly generated ID used in the keep-alive check. This value is sent by the server
   * and must be returned by the client to confirm that the connection is still active.
   */
  private long randomId;

  /**
   * Gets the randomly generated ID associated with this keep-alive packet.
   * This ID must be echoed back by the client to validate the connection.
   *
   * @return the keep-alive ID
   */
  public long getRandomId() {
    return randomId;
  }

  /**
   * Sets the randomly generated ID for this keep-alive packet.
   *
   * @param randomId the keep-alive ID to set
   */
  public void setRandomId(final long randomId) {
    this.randomId = randomId;
  }

  @Override
  public final String toString() {
    return "KeepAlive{"
        + "randomId=" + randomId
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_12_2)) {
      randomId = buf.readLong();
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      randomId = ProtocolUtils.readVarInt(buf);
    } else {
      randomId = buf.readInt();
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_12_2)) {
      buf.writeLong(randomId);
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      ProtocolUtils.writeVarInt(buf, (int) randomId);
    } else {
      buf.writeInt((int) randomId);
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
