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

import static com.velocitypowered.proxy.protocol.util.PluginMessageUtil.transformLegacyToModernChannel;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.protocol.util.DeferredByteBufHolder;
import io.netty.buffer.ByteBuf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a plugin message packet, which allows for custom communication between
 * a Minecraft server and a client via custom channels.
 */
public class PluginMessagePacket extends DeferredByteBufHolder implements MinecraftPacket {

  /**
   * The channel name for the plugin message.
   * This field specifies the logical destination or purpose of the plugin message.
   * Can be {@code null} before decoding or initialization.
   */
  private @Nullable String channel;

  /**
   * Constructs a new {@code PluginMessagePacket} with no initial data or channel.
   * This is primarily used during decoding.
   */
  public PluginMessagePacket() {
    super(null);
  }

  /**
   * Constructs a new {@code PluginMessagePacket} with the specified channel and backing buffer.
   *
   * @param channel the channel name to send the plugin message to, or {@code null} if unset
   * @param backing the {@link ByteBuf} containing the plugin message payload
   */
  public PluginMessagePacket(final @Nullable String channel,
                             final @MonotonicNonNull ByteBuf backing) {
    super(backing);
    this.channel = channel;
  }

  /**
   * Gets the channel for this plugin message.
   *
   * @return the channel name
   * @throws IllegalStateException if the channel is not set
   */
  public String getChannel() {
    if (channel == null) {
      throw new IllegalStateException("Channel is not specified.");
    }

    return channel;
  }

  /**
   * Sets the plugin message channel.
   *
   * @param channel the channel name, or {@code null} to unset
   */
  public void setChannel(final @Nullable String channel) {
    this.channel = channel;
  }

  /**
   * Returns a string representation of this plugin message packet for debugging purposes.
   *
   * <p>This includes the channel name and a reference to the underlying byte buffer data.</p>
   *
   * @return a string representation of the plugin message
   */
  @Override
  public String toString() {
    return "PluginMessage{"
        + "channel='" + channel + '\''
        + ", data=" + super.toString()
        + '}';
  }

  /**
   * Decodes this plugin message packet from the given {@link ByteBuf}.
   *
   * <p>This method reads the channel string and message payload from the buffer.
   * The channel name is optionally transformed based on the protocol version.</p>
   *
   * @param buf the buffer containing the encoded packet
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the protocol version used to determine encoding rules
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    this.channel = ProtocolUtils.readString(buf);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_13)) {
      this.channel = transformLegacyToModernChannel(this.channel);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      this.replace(buf.readRetainedSlice(buf.readableBytes()));
    } else {
      this.replace(ProtocolUtils.readRetainedByteBufSlice17(buf));
    }
  }

  /**
   * Encodes this plugin message packet into the given {@link ByteBuf}.
   *
   * <p>This method writes the channel and message payload according to the protocol version.
   * A legacy-to-modern channel name mapping is applied for 1.13+ clients.</p>
   *
   * @param buf the buffer to write the encoded packet to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the protocol version used to determine encoding rules
   * @throws IllegalStateException if the channel is not set or the payload was released
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (channel == null) {
      throw new IllegalStateException("Channel is not specified.");
    }

    if (refCnt() == 0) {
      throw new IllegalStateException("Plugin message contents for " + this.channel
          + " freed too many times.");
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_13)) {
      ProtocolUtils.writeString(buf, transformLegacyToModernChannel(this.channel));
    } else {
      ProtocolUtils.writeString(buf, this.channel);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      buf.writeBytes(content());
    } else {
      ProtocolUtils.writeByteBuf17(content(), buf, true); // True for Forge support
    }
  }

  /**
   * Handles this plugin message packet using the given {@link MinecraftSessionHandler}.
   *
   * <p>This delegates handling to the session handler’s {@code handle(PluginMessagePacket)} method.</p>
   *
   * @param handler the session handler to process the packet
   * @return {@code true} if the packet was handled successfully; {@code false} otherwise
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Creates a deep copy of this plugin message packet.
   *
   * <p>This includes copying the internal buffer and retaining the same channel name.</p>
   *
   * @return a copied instance of this packet
   */
  @Override
  public PluginMessagePacket copy() {
    return (PluginMessagePacket) super.copy();
  }

  /**
   * Creates a shallow duplicate of this plugin message packet.
   *
   * <p>The content buffer is shared between the original and the duplicate.</p>
   *
   * @return a duplicate instance of this packet
   */
  @Override
  public PluginMessagePacket duplicate() {
    return (PluginMessagePacket) super.duplicate();
  }

  /**
   * Creates a shallow duplicate of this plugin message packet and retains the buffer.
   *
   * <p>This is used when buffer reference counting must be preserved during duplication.</p>
   *
   * @return a retained duplicate of this packet
   */
  @Override
  public PluginMessagePacket retainedDuplicate() {
    return (PluginMessagePacket) super.retainedDuplicate();
  }

  /**
   * Replaces the current packet payload with the given {@link ByteBuf}.
   *
   * <p>The new content will be used while preserving the existing channel.</p>
   *
   * @param content the new payload buffer
   * @return a new {@code PluginMessagePacket} with the updated content
   */
  @Override
  public PluginMessagePacket replace(final ByteBuf content) {
    return (PluginMessagePacket) super.replace(content);
  }

  /**
   * Increments the reference count of this packet’s content buffer.
   *
   * <p>This allows the buffer to be safely reused across Netty operations.</p>
   *
   * @return this packet instance
   */
  @Override
  public PluginMessagePacket retain() {
    return (PluginMessagePacket) super.retain();
  }

  /**
   * Increments the reference count of this packet’s content buffer by the specified amount.
   *
   * @param increment the number of references to add
   * @return this packet instance
   */
  @Override
  public PluginMessagePacket retain(final int increment) {
    return (PluginMessagePacket) super.retain(increment);
  }

  /**
   * Marks this packet as accessed for debugging purposes.
   *
   * <p>This helps track potential memory leaks during buffer lifecycle debugging.</p>
   *
   * @return this packet instance
   */
  @Override
  public PluginMessagePacket touch() {
    return (PluginMessagePacket) super.touch();
  }

  /**
   * Marks this packet as accessed and associates a hint object for debugging.
   *
   * @param hint the hint object to associate for tracking purposes
   * @return this packet instance
   */
  @Override
  public PluginMessagePacket touch(final Object hint) {
    return (PluginMessagePacket) super.touch(hint);
  }

  /**
   * Provides an estimated number of bytes required to encode this plugin message packet.
   *
   * <p>This implementation returns the number of readable bytes in the underlying payload buffer,
   * representing the size of the actual plugin message data. The full encoded packet will also
   * include the UTF-8 encoded channel name and its length prefix written by
   * {@link #encode(ByteBuf, Direction, ProtocolVersion)}.</p>
   *
   * <p>This estimate is primarily used by the encoder to preallocate sufficient buffer space,
   * minimizing reallocation and improving performance during network writes.</p>
   *
   * @param direction the packet direction (clientbound or serverbound)
   * @param version the Minecraft protocol version
   * @return the estimated payload size in bytes, equal to the readable byte count of the content
   */
  @Override
  public int encodeSizeHint(final Direction direction, final ProtocolVersion version) {
    return content().readableBytes();
  }
}
