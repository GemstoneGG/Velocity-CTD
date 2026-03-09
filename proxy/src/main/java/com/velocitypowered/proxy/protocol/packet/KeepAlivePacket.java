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
  public String toString() {
    return "KeepAlive{"
        + "randomId=" + randomId
        + '}';
  }

  /**
   * Decodes this keep-alive packet from the given {@link ByteBuf}.
   *
   * <p>This method reads the keep-alive ID according to the protocol version. Depending
   * on the version, the ID is encoded as a {@code long}, {@code varint}, or {@code int}.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_12_2)) {
      randomId = buf.readLong();
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      randomId = ProtocolUtils.readVarInt(buf);
    } else {
      randomId = buf.readInt();
    }
  }

  /**
   * Encodes this keep-alive packet into the given {@link ByteBuf}.
   *
   * <p>This method writes the keep-alive ID using the appropriate encoding for
   * the specified protocol version.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_12_2)) {
      buf.writeLong(randomId);
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      ProtocolUtils.writeVarInt(buf, (int) randomId);
    } else {
      buf.writeInt((int) randomId);
    }
  }

  /**
   * Handles this keep-alive packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} for further
   * connection validation logic.</p>
   *
   * @param handler the session handler to process the packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
