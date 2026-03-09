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
 * Represents a packet that sets the compression threshold for network communication.
 * When the size of a packet exceeds the threshold, the packet will be compressed.
 */
public class SetCompressionPacket implements MinecraftPacket {

  /**
   * The compression threshold in bytes.
   *
   * <p>Any packets larger than this threshold will be compressed.</p>
   */
  private int threshold;

  /**
   * Constructs an empty {@code SetCompressionPacket}.
   *
   * <p>Fields must be populated before encoding.</p>
   */
  public SetCompressionPacket() {
  }

  /**
   * Constructs a {@code SetCompressionPacket} with the given compression threshold.
   *
   * @param threshold the compression threshold in bytes
   */
  public SetCompressionPacket(final int threshold) {
    this.threshold = threshold;
  }

  /**
   * Gets the compression threshold value.
   *
   * @return the compression threshold in bytes
   */
  public int getThreshold() {
    return threshold;
  }

  /**
   * Sets the compression threshold value.
   *
   * @param threshold the compression threshold in bytes
   */
  public void setThreshold(final int threshold) {
    this.threshold = threshold;
  }

  @Override
  public String toString() {
    return "SetCompression{"
        + "threshold=" + threshold
        + '}';
  }

  /**
   * Decodes this compression packet from the given {@link ByteBuf}.
   *
   * <p>This reads the compression threshold value encoded as a VarInt.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    this.threshold = ProtocolUtils.readVarInt(buf);
  }

  /**
   * Encodes this compression packet into the given {@link ByteBuf}.
   *
   * <p>This writes the compression threshold value as a VarInt.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    ProtocolUtils.writeVarInt(buf, threshold);
  }

  /**
   * Handles this compression packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to apply compression settings.</p>
   *
   * @param handler the session handler responsible for handling this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
