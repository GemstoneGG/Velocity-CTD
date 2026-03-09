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

  /**
   * Returns a string representation of this handshake packet for debugging purposes.
   *
   * <p>This includes the protocol version, server address, port, and next status.</p>
   *
   * @return a string containing the protocol version, server address, port, and next status
   */
  @Override
  public String toString() {
    return "Handshake{"
        + "protocolVersion=" + protocolVersion
        + ", serverAddress='" + serverAddress + '\''
        + ", port=" + port
        + ", nextStatus=" + nextStatus
        + '}';
  }

  /**
   * Decodes this handshake packet from the provided {@link ByteBuf}.
   *
   * <p>This method reads the protocol version, server address, port, and next connection intent
   * from the buffer, and sets the appropriate fields on this packet.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param ignored the protocol version (not used for handshake decoding)
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion ignored) {
    int realProtocolVersion = ProtocolUtils.readVarInt(buf);
    this.protocolVersion = ProtocolVersion.getProtocolVersion(realProtocolVersion);
    this.serverAddress = ProtocolUtils.readString(buf, MAXIMUM_HOSTNAME_LENGTH);
    this.port = buf.readUnsignedShort();
    this.nextStatus = ProtocolUtils.readVarInt(buf);
    this.intent = HandshakeIntent.getById(nextStatus);
  }

  /**
   * Encodes this handshake packet into the provided {@link ByteBuf}.
   *
   * <p>This method writes the protocol version, server address, port, and next connection intent
   * to the buffer.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param ignored the protocol version (not used for handshake encoding)
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion ignored) {
    ProtocolUtils.writeVarInt(buf, this.protocolVersion.getProtocol());
    ProtocolUtils.writeString(buf, this.serverAddress);
    buf.writeShort(this.port);
    ProtocolUtils.writeVarInt(buf, this.nextStatus);
  }

  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Returns a conservative lower bound (in bytes) for a valid handshake packet.
   *
   * <p>This minimum accounts for the smallest-possible encodings of the fixed fields:
   * <ul>
   *   <li>protocol version (VarInt, minimal form),</li>
   *   <li>an empty server address (string length prefix only),</li>
   *   <li>port (2 bytes), and</li>
   *   <li>next state / intent (VarInt, minimal form).</li>
   * </ul>
   * The returned value is intentionally conservative to avoid false negatives during
   * early-length validation.</p>
   *
   * @param buf the buffer being decoded (not used for this calculation)
   * @param direction the packet direction
   * @param version the protocol version
   * @return a conservative minimum expected byte length
   */
  @Override
  public int decodeExpectedMinLength(final ByteBuf buf, final ProtocolUtils.Direction direction,
                                     final ProtocolVersion version) {
    return 7;
  }

  /**
   * Returns a conservative upper bound (in bytes) for a handshake packet.
   *
   * <p>This bound includes:
   * <ul>
   *   <li>the protocol version (VarInt, up to several bytes),</li>
   *   <li>the server address string length prefix (VarInt),</li>
   *   <li>the server address itself, sized pessimistically as up to 3 bytes per character
   *       to account for worst-case UTF-8 expansion, using {@code MAXIMUM_HOSTNAME_LENGTH},</li>
   *   <li>the port (2 bytes), and</li>
   *   <li>the next state / intent (VarInt).</li>
   * </ul>
   * The constant overhead added before the {@code * 3} term covers the non-hostname
   * fields and length-prefix worst case.</p>
   *
   * @param buf the buffer being decoded (not used for this calculation)
   * @param direction the packet direction
   * @param version the protocol version
   * @return a conservative maximum expected byte length
   */
  @Override
  public int decodeExpectedMaxLength(final ByteBuf buf, final ProtocolUtils.Direction direction,
                                     final ProtocolVersion version) {
    return 9 + (MAXIMUM_HOSTNAME_LENGTH * 3);
  }

  /**
   * Provides an estimated encoded size (in bytes) of this packet for buffer preallocation.
   *
   * <p>The handshake is small but the hostname can vary; rather than compute an exact size
   * on every call, this returns a safe fixed estimate ({@code 4 KiB}) that comfortably
   * covers all reasonable cases and minimizes buffer reallocation during encoding.
   * The encoder will add the VarInt packet ID on top of this size when allocating.</p>
   *
   * @param direction the packet direction
   * @param version the protocol version
   * @return the estimated payload size in bytes (here, {@code 4096})
   */
  @Override
  public int encodeSizeHint(final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    // We could compute an exact size, but 4KiB ought to be enough to encode all reasonable
    // sizes of this packet.
    return 4 * 1024;
  }
}
