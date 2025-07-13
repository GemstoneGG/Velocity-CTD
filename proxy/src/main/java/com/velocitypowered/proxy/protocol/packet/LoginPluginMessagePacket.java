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
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a login plugin message packet sent during the login phase. This packet allows custom
 * plugin messages to be sent from the server to the client before login is complete.
 */
public class LoginPluginMessagePacket extends DeferredByteBufHolder implements MinecraftPacket {

  /**
   * The plugin message ID, used to track the response from the client.
   */
  private int id;

  /**
   * The channel this plugin message is being sent on.
   * This may be {@code null} during deserialization until explicitly set.
   */
  private @Nullable String channel;

  /**
   * Constructs an empty {@code LoginPluginMessagePacket} with no pre-initialized buffer.
   *
   * <p>This constructor is primarily used during decoding. The buffer content will be set later
   * via {@link #decode(ByteBuf, ProtocolUtils.Direction, ProtocolVersion)}.</p>
   */
  public LoginPluginMessagePacket() {
    super(null);
  }

  /**
   * Constructs a new {@code LoginPluginMessagePacket} with the specified ID, channel, and data buffer.
   *
   * @param id the plugin message ID
   * @param channel the channel name, or {@code null} if not specified
   * @param data the data buffer
   */
  public LoginPluginMessagePacket(final int id, @Nullable final String channel, final ByteBuf data) {
    super(data);
    this.id = id;
    this.channel = channel;
  }

  /**
   * Returns the plugin message ID.
   *
   * @return the plugin message ID
   */
  public int getId() {
    return id;
  }

  /**
   * Gets the plugin message channel.
   *
   * @return the channel name
   * @throws IllegalStateException if the channel is not specified
   */
  public String getChannel() {
    if (channel == null) {
      throw new IllegalStateException("Channel is not specified!");
    }

    return channel;
  }

  @Override
  public String toString() {
    return "LoginPluginMessage{"
        + "proxyId=" + id
        + ", channel='" + channel + '\''
        + ", data=" + super.toString()
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    this.id = ProtocolUtils.readVarInt(buf);
    this.channel = ProtocolUtils.readString(buf);
    if (buf.isReadable()) {
      this.replace(buf.readRetainedSlice(buf.readableBytes()));
    } else {
      this.replace(Unpooled.EMPTY_BUFFER);
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    ProtocolUtils.writeVarInt(buf, id);
    if (channel == null) {
      throw new IllegalStateException("Channel is not specified!");
    }

    ProtocolUtils.writeString(buf, channel);
    buf.writeBytes(content());
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
