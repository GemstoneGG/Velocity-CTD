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

package com.velocitypowered.proxy.protocol.packet.chat.keyed;

import com.google.common.primitives.Longs;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.crypto.EncryptionUtils;
import com.velocitypowered.proxy.crypto.SignaturePair;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a player chat packet with support for message signing and preview.
 *
 * <p>The {@code KeyedPlayerChatPacket} handles player chat messages, supporting signed previews,
 * message signatures, and previous message validation. It includes fields for tracking message
 * signatures and handling expired messages.</p>
 */
public class KeyedPlayerChatPacket implements MinecraftPacket {

  /**
   * The raw message content sent by the client.
   */
  private String message;

  /**
   * Whether this message includes a signed preview (1.19+).
   */
  private boolean signedPreview;

  /**
   * Whether this message is unsigned (determined during decoding).
   */
  private boolean unsigned = false;

  /**
   * The expiration timestamp for signed messages.
   */
  private @Nullable Instant expiry;

  /**
   * The cryptographic signature associated with this message.
   */
  private byte[] signature;

  /**
   * The salt used during signing.
   */
  private byte[] salt;

  /**
   * A list of previously acknowledged message signatures (1.19.1+).
   */
  private SignaturePair[] previousMessages = new SignaturePair[0];

  /**
   * The last known message signature acknowledged by the client (1.19.1+).
   */
  private @Nullable SignaturePair lastMessage;

  /**
   * The maximum number of previous message signatures allowed in a single packet.
   */
  public static final int MAXIMUM_PREVIOUS_MESSAGE_COUNT = 5;

  /**
   * Thrown when the previous message signature count is out of range.
   */
  public static final QuietDecoderException INVALID_PREVIOUS_MESSAGES =
      new QuietDecoderException("Invalid previous messages");

  /**
   * Constructs a blank {@code KeyedPlayerChatPacket} for deserialization.
   */
  public KeyedPlayerChatPacket() {
  }

  /**
   * Constructs a new {@code KeyedPlayerChatPacket} with an unsigned message.
   *
   * @param message the raw message to send
   */
  public KeyedPlayerChatPacket(final String message) {
    this.message = message;
    this.unsigned = true;
  }

  /**
   * Sets the expiration time for the signed message.
   *
   * @param expiry the message expiration timestamp
   */
  public void setExpiry(final @Nullable Instant expiry) {
    this.expiry = expiry;
  }

  /**
   * Returns the expiration time for the message.
   *
   * @return the timestamp, or {@code null} if not applicable
   */
  public @Nullable Instant getExpiry() {
    return expiry;
  }

  /**
   * Returns whether this message is unsigned.
   *
   * @return {@code true} if unsigned; otherwise {@code false}
   */
  public boolean isUnsigned() {
    return unsigned;
  }

  /**
   * Returns the message text.
   *
   * @return the message string
   */
  public String getMessage() {
    return message;
  }

  /**
   * Returns whether the message was sent with a signed preview.
   *
   * @return {@code true} if preview is signed
   */
  public boolean isSignedPreview() {
    return signedPreview;
  }

  /**
   * Decodes this keyed player chat packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the message text, optional signature, preview flag, expiration timestamp,
   * and previous message signature metadata depending on the protocol version.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the Minecraft protocol version
   * @throws QuietDecoderException if signature data is malformed or too many previous messages
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    message = ProtocolUtils.readString(buf, 256);

    long expiresAt = buf.readLong();
    long saltLong = buf.readLong();
    byte[] signatureBytes = ProtocolUtils.readByteArray(buf);

    if (saltLong != 0L && signatureBytes.length > 0) {
      salt = Longs.toByteArray(saltLong);
      signature = signatureBytes;
      expiry = Instant.ofEpochMilli(expiresAt);
    } else if ((protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)
        || saltLong == 0L) && signatureBytes.length == 0) {
      unsigned = true;
    } else {
      throw EncryptionUtils.INVALID_SIGNATURE;
    }

    signedPreview = buf.readBoolean();
    if (signedPreview && unsigned) {
      throw EncryptionUtils.PREVIEW_SIGNATURE_MISSING;
    }

    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
      int size = ProtocolUtils.readVarInt(buf);
      if (size < 0 || size > MAXIMUM_PREVIOUS_MESSAGE_COUNT) {
        throw INVALID_PREVIOUS_MESSAGES;
      }

      SignaturePair[] lastSignatures = new SignaturePair[size];
      for (int i = 0; i < size; i++) {
        lastSignatures[i] = new SignaturePair(ProtocolUtils.readUuid(buf),
            ProtocolUtils.readByteArray(buf));
      }

      previousMessages = lastSignatures;

      if (buf.readBoolean()) {
        lastMessage = new SignaturePair(ProtocolUtils.readUuid(buf),
            ProtocolUtils.readByteArray(buf));
      }
    }
  }

  /**
   * Encodes this keyed player chat packet into the provided {@link ByteBuf}.
   *
   * <p>This writes the message, signature (or placeholder), salt, preview flag,
   * and prior message acknowledgment metadata according to the protocol version.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the Minecraft protocol version
   * @throws NullPointerException if required fields are missing when not unsigned
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeString(buf, message);

    buf.writeLong(unsigned ? Instant.now().toEpochMilli() : Objects.requireNonNull(expiry).toEpochMilli());
    buf.writeLong(unsigned ? 0L : Longs.fromByteArray(salt));

    ProtocolUtils.writeByteArray(buf, unsigned ? EncryptionUtils.EMPTY : signature);

    buf.writeBoolean(signedPreview);

    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
      ProtocolUtils.writeVarInt(buf, previousMessages.length);
      for (SignaturePair previousMessage : previousMessages) {
        ProtocolUtils.writeUuid(buf, previousMessage.getSigner());
        ProtocolUtils.writeByteArray(buf, previousMessage.getSignature());
      }

      if (lastMessage != null) {
        buf.writeBoolean(true);
        ProtocolUtils.writeUuid(buf, lastMessage.getSigner());
        ProtocolUtils.writeByteArray(buf, lastMessage.getSignature());
      } else {
        buf.writeBoolean(false);
      }
    }
  }

  /**
   * Handles this keyed player chat packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to verify or process
   * the message content, signature, and metadata.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
