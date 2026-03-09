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
import java.net.InetSocketAddress;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a packet used to transfer a player to another server.
 */
public class TransferPacket implements MinecraftPacket {

  /**
   * The hostname of the server to transfer the player to.
   */
  private String host;

  /**
   * The port of the server to transfer the player to.
   */
  private int port;

  /**
   * Constructs an empty {@code TransferPacket}. Used primarily for decoding.
   */
  public TransferPacket() {
  }

  /**
   * Constructs a {@code TransferPacket} with the specified host and port.
   *
   * @param host the hostname of the destination server
   * @param port the port of the destination server
   */
  public TransferPacket(final String host, final int port) {
    this.host = host;
    this.port = port;
  }

  /**
   * Gets the {@link InetSocketAddress} representing the transfer address.
   *
   * @return the {@code InetSocketAddress}, or {@code null} if the host is not set
   */
  @Nullable
  public InetSocketAddress address() {
    if (host == null) {
      return null;
    }

    return new InetSocketAddress(host, port);
  }

  /**
   * Decodes this transfer packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the hostname and port number of the destination server
   * that the player should be transferred to.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    this.host = ProtocolUtils.readString(buf);
    this.port = ProtocolUtils.readVarInt(buf);
  }

  /**
   * Encodes this transfer packet into the provided {@link ByteBuf}.
   *
   * <p>This writes the hostname and port of the target server for player transfer.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeString(buf, host);
    ProtocolUtils.writeVarInt(buf, port);
  }

  /**
   * Handles this transfer packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to initiate
   * transfer of the player to another backend server.</p>
   *
   * @param handler the session handler responsible for handling this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
