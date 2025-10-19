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
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
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
  public StatusResponsePacket(final @Nullable CharSequence status) {
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

  /**
   * Returns a string representation of this status response packet.
   *
   * <p>This includes the JSON-like status string representing the server response.</p>
   *
   * @return a string describing the packet
   */
  @Override
  public String toString() {
    return "StatusResponse{"
        + "status='" + status + '\''
        + '}';
  }

  /**
   * Decodes this status response packet from the given {@link ByteBuf}.
   *
   * <p>This reads the server status message as a {@link String}, typically JSON-formatted.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    status = ProtocolUtils.readString(buf, Short.MAX_VALUE);
  }

  /**
   * Encodes this status response packet into the given {@link ByteBuf}.
   *
   * <p>This writes the server status message as a {@link String}, typically JSON-formatted.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   * @throws IllegalStateException if the status is not specified
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (status == null) {
      throw new IllegalStateException("Status is not specified");
    }

    ProtocolUtils.writeString(buf, status);
  }

  /**
   * Handles this status response packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to complete the client's
   * status query handshake.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Provides an estimated number of bytes required to encode this status response packet.
   *
   * <p>The encoded size corresponds to the UTF-8 encoded length of the status message,
   * typically containing the JSON-formatted server status data. This estimate is obtained
   * by calling {@link ProtocolUtils#stringSizeHint(CharSequence)}, which accounts for
   * the VarInt string length prefix and the UTF-8 byte count of the message itself.</p>
   *
   * <p>This hint allows the encoder to preallocate a buffer large enough to contain
   * the entire encoded message, minimizing reallocation during write operations.</p>
   *
   * @param direction the packet direction (clientbound or serverbound)
   * @param version the Minecraft protocol version
   * @return the estimated encoded size of this packet in bytes
   */
  @Override
  public int encodeSizeHint(final Direction direction, final ProtocolVersion version) {
    return ProtocolUtils.stringSizeHint(this.status);
  }
}
