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
import com.velocitypowered.proxy.protocol.util.DeferredByteBufHolder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Represents the response packet to a plugin message sent during the login phase.
 * The packet contains the plugin message ID, a success flag, and any additional data.
 */
public class LoginPluginResponsePacket extends DeferredByteBufHolder implements MinecraftPacket {

  /**
   * The ID of the plugin message being responded to.
   * This value is used to correlate responses with their original plugin requests.
   */
  private int id;

  /**
   * Indicates whether the plugin message was successfully handled by the client.
   */
  private boolean success;

  /**
   * Constructs an empty {@code LoginPluginResponsePacket} with an empty buffer.
   *
   * <p>This constructor is typically used during decoding. The buffer content and other fields
   * will be set via {@link #decode(ByteBuf, ProtocolUtils.Direction, ProtocolVersion)}.</p>
   */
  public LoginPluginResponsePacket() {
    super(Unpooled.EMPTY_BUFFER);
  }

  /**
   * Constructs a new {@code LoginPluginResponsePacket} with the specified ID, success status, and data buffer.
   *
   * @param id the plugin message ID
   * @param success {@code true} if the plugin message was successful, {@code false} otherwise
   * @param buf the data buffer
   */
  public LoginPluginResponsePacket(final int id, final boolean success, final @MonotonicNonNull ByteBuf buf) {
    super(buf);
    this.id = id;
    this.success = success;
  }

  /**
   * Retrieves the plugin message ID associated with this response.
   *
   * @return the plugin message ID
   */
  public int getId() {
    return id;
  }

  /**
   * Sets the plugin message ID for this response.
   *
   * @param id the plugin message ID to set
   */
  public void setId(final int id) {
    this.id = id;
  }

  /**
   * Returns whether the plugin message response indicates success.
   *
   * @return {@code true} if the plugin message was successful, {@code false} otherwise
   */
  public boolean isSuccess() {
    return success;
  }

  /**
   * Sets whether the plugin message was successful.
   *
   * @param success {@code true} if the plugin message was successful, {@code false} otherwise
   */
  public void setSuccess(final boolean success) {
    this.success = success;
  }

  /**
   * Returns a string representation of this login plugin response packet.
   *
   * <p>This includes the plugin message ID, success status, and content buffer.</p>
   *
   * @return a string representation of the packet
   */
  @Override
  public String toString() {
    return "LoginPluginResponse{"
        + "proxyId=" + id
        + ", success=" + success
        + ", data=" + super.toString()
        + '}';
  }

  /**
   * Decodes this login plugin response packet from the provided {@link ByteBuf}.
   *
   * <p>This method reads the plugin message ID, success flag, and any remaining payload
   * data from the buffer, retaining it in the internal buffer.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the protocol version being used
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    this.id = ProtocolUtils.readVarInt(buf);
    this.success = buf.readBoolean();
    if (buf.isReadable()) {
      this.replace(buf.readRetainedSlice(buf.readableBytes()));
    } else {
      this.replace(Unpooled.EMPTY_BUFFER);
    }
  }

  /**
   * Encodes this login plugin response packet into the provided {@link ByteBuf}.
   *
   * <p>This method writes the plugin message ID, success flag, and any payload data
   * to the output buffer.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the protocol version being used
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    ProtocolUtils.writeVarInt(buf, id);
    buf.writeBoolean(success);
    buf.writeBytes(content());
  }

  /**
   * Handles this login plugin response packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)}.</p>
   *
   * @param handler the session handler to process the packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
