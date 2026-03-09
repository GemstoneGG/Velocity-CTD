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
import com.velocitypowered.proxy.protocol.packet.legacyping.LegacyMinecraftPingVersion;
import io.netty.buffer.ByteBuf;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a legacy ping packet in Minecraft, commonly used in the server list ping process.
 * This packet handles compatibility with older Minecraft versions and contains information
 * such as the ping protocol version and optionally a virtual host address.
 */
public class LegacyPingPacket implements MinecraftPacket {

  /**
   * The legacy Minecraft ping protocol version used in this packet.
   */
  private final LegacyMinecraftPingVersion version;

  /**
   * The virtual host address for the ping, if applicable. May be {@code null}.
   */
  private final @Nullable InetSocketAddress vhost;

  /**
   * Constructs a legacy ping packet with the specified ping protocol version.
   *
   * @param version the {@link LegacyMinecraftPingVersion} being used
   */
  public LegacyPingPacket(final LegacyMinecraftPingVersion version) {
    this.version = version;
    this.vhost = null;
  }

  /**
   * Constructs a legacy ping packet with the specified ping protocol version and virtual host.
   *
   * @param version the {@link LegacyMinecraftPingVersion} being used
   * @param vhost the virtual host address, or {@code null} if not specified
   */
  public LegacyPingPacket(final LegacyMinecraftPingVersion version, final @Nullable InetSocketAddress vhost) {
    this.version = version;
    this.vhost = vhost;
  }

  /**
   * Returns the legacy Minecraft ping version.
   *
   * @return the {@link LegacyMinecraftPingVersion} in use
   */
  public LegacyMinecraftPingVersion getVersion() {
    return version;
  }

  /**
   * Returns the virtual host associated with the ping, if available.
   *
   * @return the {@link InetSocketAddress} for the virtual host, or {@code null} if not present
   */
  public @Nullable InetSocketAddress getVhost() {
    return vhost;
  }

  /**
   * Throws an {@link UnsupportedOperationException} because decoding is not supported.
   *
   * <p>This packet is used only for internal routing of legacy pings and is not decoded from the wire.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   * @throws UnsupportedOperationException always
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    throw new UnsupportedOperationException();
  }

  /**
   * Throws an {@link UnsupportedOperationException} because encoding is not supported.
   *
   * <p>This packet is used only for legacy inbound ping handling and is not serialized for transmission.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   * @throws UnsupportedOperationException always
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    throw new UnsupportedOperationException();
  }

  /**
   * Handles this legacy ping packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to perform compatibility handling.</p>
   *
   * @param handler the session handler to process the packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
