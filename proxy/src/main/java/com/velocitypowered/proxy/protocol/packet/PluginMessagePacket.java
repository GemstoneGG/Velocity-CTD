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

import static com.velocitypowered.proxy.protocol.util.PluginMessageUtil.transformLegacyToModernChannel;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
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
  public PluginMessagePacket(@Nullable final String channel,
                             @MonotonicNonNull final ByteBuf backing) {
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
  public void setChannel(@Nullable final String channel) {
    this.channel = channel;
  }

  @Override
  public String toString() {
    return "PluginMessage{"
        + "channel='" + channel + '\''
        + ", data=" + super.toString()
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
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

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
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

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public final PluginMessagePacket copy() {
    return (PluginMessagePacket) super.copy();
  }

  @Override
  public final PluginMessagePacket duplicate() {
    return (PluginMessagePacket) super.duplicate();
  }

  @Override
  public final PluginMessagePacket retainedDuplicate() {
    return (PluginMessagePacket) super.retainedDuplicate();
  }

  @Override
  public final PluginMessagePacket replace(final ByteBuf content) {
    return (PluginMessagePacket) super.replace(content);
  }

  @Override
  public final PluginMessagePacket retain() {
    return (PluginMessagePacket) super.retain();
  }

  @Override
  public final PluginMessagePacket retain(final int increment) {
    return (PluginMessagePacket) super.retain(increment);
  }

  @Override
  public PluginMessagePacket touch() {
    return (PluginMessagePacket) super.touch();
  }

  @Override
  public final PluginMessagePacket touch(final Object hint) {
    return (PluginMessagePacket) super.touch(hint);
  }
}
