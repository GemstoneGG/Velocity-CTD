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

package com.velocitypowered.proxy.protocol.packet.chat.session;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.LastSeenMessages;
import io.netty.buffer.ByteBuf;
import java.time.Instant;

/**
 * Represents a player chat packet specific to a session, implementing {@link MinecraftPacket}.
 *
 * <p>The {@code SessionPlayerChatPacket} handles chat messages sent by a player during a session,
 * and may include session-specific context, such as timestamps, message formatting, or other
 * relevant session data.</p>
 */
public class SessionPlayerChatPacket implements MinecraftPacket {

  /**
   * The raw message content provided by the player.
   */
  protected String message;

  /**
   * The timestamp indicating when the message was sent.
   */
  protected Instant timestamp;

  /**
   * A random salt used in the signature computation.
   */
  protected long salt;

  /**
   * Indicates whether the message is signed by the client.
   */
  protected boolean signed;

  /**
   * The cryptographic signature for this message (256 bytes), if {@link #signed} is {@code true}.
   */
  protected byte[] signature;

  /**
   * The last seen message metadata used for validating context and preventing spoofing.
   */
  protected LastSeenMessages lastSeenMessages;

  /**
   * Constructs an empty {@code SessionPlayerChatPacket} for decoding purposes.
   */
  public SessionPlayerChatPacket() {
  }

  /**
   * Returns the raw message string.
   *
   * @return the message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Returns the message's timestamp.
   *
   * @return the timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Returns the salt used during signing.
   *
   * @return the salt
   */
  public long getSalt() {
    return salt;
  }

  /**
   * Returns whether the message is signed.
   *
   * @return {@code true} if signed, {@code false} otherwise
   */
  public boolean isSigned() {
    return signed;
  }

  /**
   * Returns the cryptographic signature.
   *
   * @return a byte array containing the signature (or empty if unsigned)
   */
  public byte[] getSignature() {
    return signature;
  }

  /**
   * Returns the last seen messages metadata.
   *
   * @return the {@link LastSeenMessages}
   */
  public LastSeenMessages getLastSeenMessages() {
    return lastSeenMessages;
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    this.message = ProtocolUtils.readString(buf, 256);
    this.timestamp = Instant.ofEpochMilli(buf.readLong());
    this.salt = buf.readLong();
    this.signed = buf.readBoolean();
    if (this.signed) {
      this.signature = readMessageSignature(buf);
    } else {
      this.signature = new byte[0];
    }

    this.lastSeenMessages = new LastSeenMessages(buf, protocolVersion);
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeString(buf, this.message);
    buf.writeLong(this.timestamp.toEpochMilli());
    buf.writeLong(this.salt);
    buf.writeBoolean(this.signed);
    if (this.signed) {
      buf.writeBytes(this.signature);
    }

    this.lastSeenMessages.encode(buf, protocolVersion);
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Reads a fixed-length (256 byte) signature from the buffer.
   *
   * @param buf the buffer
   * @return the 256-byte signature
   */
  protected static byte[] readMessageSignature(final ByteBuf buf) {
    byte[] signature = new byte[256];
    buf.readBytes(signature);
    return signature;
  }

  /**
   * Creates a new {@code SessionPlayerChatPacket} with the specified last-seen messages.
   *
   * <p>This method constructs a new {@code SessionPlayerChatPacket} instance that retains the
   * current packet's properties, while updating the last seen messages.</p>
   *
   * @param lastSeenMessages the last seen messages to associate with the new packet
   * @return a new {@code SessionPlayerChatPacket} with the updated last seen messages
   */
  public SessionPlayerChatPacket withLastSeenMessages(final LastSeenMessages lastSeenMessages) {
    SessionPlayerChatPacket packet = new SessionPlayerChatPacket();
    packet.message = message;
    packet.timestamp = timestamp;
    packet.salt = salt;
    packet.signed = signed;
    packet.signature = signature;
    packet.lastSeenMessages = lastSeenMessages;
    return packet;
  }
}
