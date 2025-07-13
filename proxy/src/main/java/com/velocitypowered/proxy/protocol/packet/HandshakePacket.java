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

import static com.velocitypowered.proxy.connection.forge.legacy.LegacyForgeConstants.HANDSHAKE_HOSTNAME_TOKEN;

import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * Represents a handshake packet in Minecraft, which is used during the initial connection process.
 * This packet contains information such as the protocol version, server address, port, and the intent
 * of the handshake (e.g., login or status request). This packet is crucial for establishing a connection
 * between the client and the server.
 */
public class HandshakePacket implements MinecraftPacket {

  // This size was chosen to ensure Forge clients can still connect even with very long hostnames.
  // While DNS technically allows any character to be used, in practice ASCII is used.

  /**
   * The maximum length allowed for the server address string, including extra characters
   * reserved for Forge legacy token injection. This accommodates long hostnames.
   */
  private static final int MAXIMUM_HOSTNAME_LENGTH = 255 + HANDSHAKE_HOSTNAME_TOKEN.length() + 1;

  /**
   * The protocol version the client reports in the handshake.
   */
  private ProtocolVersion protocolVersion;

  /**
   * The hostname or IP address the client is attempting to connect to.
   */
  private String serverAddress = "";

  /**
   * The network port the client is connecting to.
   */
  private int port;

  /**
   * The intent of the handshake (e.g., login or status).
   */
  private HandshakeIntent intent;

  /**
   * The raw integer ID representing the next state or handshake intent.
   */
  private int nextStatus;

  /**
   * Gets the protocol version that the client is using.
   *
   * @return the client's {@link ProtocolVersion}
   */
  public ProtocolVersion getProtocolVersion() {
    return protocolVersion;
  }

  /**
   * Sets the protocol version for this handshake.
   *
   * @param protocolVersion the {@link ProtocolVersion} to set
   */
  public void setProtocolVersion(final ProtocolVersion protocolVersion) {
    this.protocolVersion = protocolVersion;
  }

  /**
   * Gets the server address the client is attempting to connect to.
   *
   * @return the target server address as a {@link String}
   */
  public String getServerAddress() {
    return serverAddress;
  }

  /**
   * Sets the server address for this handshake.
   *
   * @param serverAddress the hostname or IP address to set
   */
  public void setServerAddress(final String serverAddress) {
    this.serverAddress = serverAddress;
  }

  /**
   * Gets the server port the client is connecting to.
   *
   * @return the target server port
   */
  public int getPort() {
    return port;
  }

  /**
   * Sets the server port for this handshake.
   *
   * @param port the port number to set
   */
  public void setPort(final int port) {
    this.port = port;
  }

  /**
   * Gets the numeric representation of the handshake intent.
   *
   * <p>This is used internally to identify the next state (e.g., login or status).</p>
   *
   * @return the intent ID value
   */
  public int getNextStatus() {
    return this.nextStatus;
  }

  /**
   * Sets the intent of the handshake (e.g., login or status).
   *
   * @param intent the {@link HandshakeIntent} to set
   */
  public void setIntent(final HandshakeIntent intent) {
    this.intent = intent;
    this.nextStatus = intent.id();
  }

  /**
   * Gets the {@link HandshakeIntent} of this handshake.
   *
   * @return the intent of the handshake
   */
  public HandshakeIntent getIntent() {
    return this.intent;
  }

  @Override
  public final String toString() {
    return "Handshake{"
        + "protocolVersion=" + protocolVersion
        + ", serverAddress='" + serverAddress + '\''
        + ", port=" + port
        + ", nextStatus=" + nextStatus
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion ignored) {
    int realProtocolVersion = ProtocolUtils.readVarInt(buf);
    this.protocolVersion = ProtocolVersion.getProtocolVersion(realProtocolVersion);
    this.serverAddress = ProtocolUtils.readString(buf, MAXIMUM_HOSTNAME_LENGTH);
    this.port = buf.readUnsignedShort();
    this.nextStatus = ProtocolUtils.readVarInt(buf);
    this.intent = HandshakeIntent.getById(nextStatus);
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion ignored) {
    ProtocolUtils.writeVarInt(buf, this.protocolVersion.getProtocol());
    ProtocolUtils.writeString(buf, this.serverAddress);
    buf.writeShort(this.port);
    ProtocolUtils.writeVarInt(buf, this.nextStatus);
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public final int expectedMinLength(final ByteBuf buf, final ProtocolUtils.Direction direction,
                               final ProtocolVersion version) {
    return 7;
  }

  @Override
  public final int expectedMaxLength(final ByteBuf buf, final ProtocolUtils.Direction direction,
                               final ProtocolVersion version) {
    return 9 + (MAXIMUM_HOSTNAME_LENGTH * 3);
  }
}
