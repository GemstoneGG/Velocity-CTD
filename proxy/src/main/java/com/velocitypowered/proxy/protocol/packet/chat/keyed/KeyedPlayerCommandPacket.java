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

import static com.velocitypowered.proxy.protocol.packet.chat.keyed.KeyedPlayerChatPacket.INVALID_PREVIOUS_MESSAGES;
import static com.velocitypowered.proxy.protocol.packet.chat.keyed.KeyedPlayerChatPacket.MAXIMUM_PREVIOUS_MESSAGE_COUNT;

import com.google.common.collect.ImmutableMap;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.crypto.EncryptionUtils;
import com.velocitypowered.proxy.crypto.SignaturePair;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a player command packet with support for keyed commands.
 *
 * <p>The {@code KeyedPlayerCommandPacket} handles player commands sent to the server,
 * allowing for command execution based on specific keys. This packet can include additional
 * information such as arguments and key-based identifiers for the command.</p>
 */
public class KeyedPlayerCommandPacket implements MinecraftPacket {

  /**
   * The maximum number of command arguments allowed in a signed command packet.
   *
   * <p>Exceeding this limit will result in a decoding failure to prevent abuse or
   * invalid packet formats.</p>
   */
  private static final int MAX_NUM_ARGUMENTS = 8;

  /**
   * The maximum length (in characters) for each individual command argument name.
   *
   * <p>This ensures that keys used in the argument-signature map are reasonably sized.</p>
   */
  private static final int MAX_LENGTH_ARGUMENTS = 16;

  /**
   * Thrown when the argument map exceeds protocol limits.
   */
  private static final QuietDecoderException LIMITS_VIOLATION =
      new QuietDecoderException("Command arguments incorrect size");

  /**
   * Indicates whether the command is unsigned (no salt, no signatures).
   */
  private boolean unsigned = false;

  /**
   * The raw command name or literal string (e.g., "say", "msg", etc.).
   */
  private String command;

  /**
   * Timestamp of when the command was issued.
   */
  private Instant timestamp;

  /**
   * The cryptographic salt used to sign the command.
   */
  private long salt;

  /**
   * Whether a signed preview was enabled (used only for 1.19 compatibility).
   */
  private boolean signedPreview;

  /**
   * Signatures of previously seen chat messages (used for replay protection).
   */
  private SignaturePair[] previousMessages = new SignaturePair[0];

  /**
   * Signature of the last known message from the client.
   */
  private @Nullable SignaturePair lastMessage;

  /**
   * A mapping of argument names to their cryptographic signatures.
   */
  private Map<String, byte[]> arguments = ImmutableMap.of();

  /**
   * Returns the timestamp of the command.
   *
   * @return the instant when the command was created
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Returns whether this packet is unsigned.
   *
   * @return true if unsigned, false otherwise
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean isUnsigned() {
    return unsigned;
  }

  /**
   * Returns the command name (without leading slash).
   *
   * @return the raw command string
   */
  public String getCommand() {
    return command;
  }

  /**
   * Constructs a blank {@link KeyedPlayerCommandPacket} for deserialization.
   */
  public KeyedPlayerCommandPacket() {
  }

  /**
   * Creates an {@link KeyedPlayerCommandPacket} packet based on a command and list of arguments.
   *
   * @param command   the command to run
   * @param arguments the arguments of the command
   * @param timestamp the timestamp of the command execution
   */
  public KeyedPlayerCommandPacket(final String command, final List<String> arguments, final Instant timestamp) {
    this.unsigned = true;
    ImmutableMap.Builder<String, byte[]> builder = ImmutableMap.builder();
    arguments.forEach(entry -> builder.put(entry, EncryptionUtils.EMPTY));
    this.arguments = builder.build();
    this.timestamp = timestamp;
    this.command = command;
    this.signedPreview = false;
    this.salt = 0L;
  }

  /**
   * Decodes this keyed player command packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the command string, timestamp, salt, argument map with signatures,
   * preview flag, and optionally previous message acknowledgments depending on protocol version.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the Minecraft protocol version
   * @throws QuietDecoderException if the argument list or previous messages exceed protocol limits
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    command = ProtocolUtils.readString(buf, 256);
    timestamp = Instant.ofEpochMilli(buf.readLong());

    salt = buf.readLong();

    int mapSize = ProtocolUtils.readVarInt(buf);
    if (mapSize > MAX_NUM_ARGUMENTS) {
      throw LIMITS_VIOLATION;
    }

    // Mapped as "Argument : signature"
    ImmutableMap.Builder<String, byte[]> entries = ImmutableMap.builderWithExpectedSize(mapSize);
    for (int i = 0; i < mapSize; i++) {
      entries.put(ProtocolUtils.readString(buf, MAX_LENGTH_ARGUMENTS),
          ProtocolUtils.readByteArray(buf, unsigned ? 0 : ProtocolUtils.DEFAULT_MAX_STRING_SIZE));
    }

    arguments = entries.build();

    this.signedPreview = buf.readBoolean();
    if (unsigned && signedPreview) {
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

    if (salt == 0L && previousMessages.length == 0) {
      unsigned = true;
    }
  }

  /**
   * Encodes this keyed player command packet into the given {@link ByteBuf}.
   *
   * <p>This writes the command string, timestamp, salt, argument map with signatures,
   * and optionally previous message acknowledgments depending on protocol version.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the Minecraft protocol version
   * @throws QuietDecoderException if the argument list exceeds protocol limits
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeString(buf, command);
    buf.writeLong(timestamp.toEpochMilli());

    buf.writeLong(unsigned ? 0L : salt);

    int size = arguments.size();
    if (size > MAX_NUM_ARGUMENTS) {
      throw LIMITS_VIOLATION;
    }

    ProtocolUtils.writeVarInt(buf, size);
    for (Map.Entry<String, byte[]> entry : arguments.entrySet()) {
      // What annoys me is that this isn't "sorted"
      ProtocolUtils.writeString(buf, entry.getKey());
      ProtocolUtils.writeByteArray(buf, unsigned ? EncryptionUtils.EMPTY : entry.getValue());
    }

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
   * Returns a string representation of this keyed player command packet.
   *
   * <p>This includes command name, timestamp, cryptographic flags, and argument signatures.</p>
   *
   * @return a string describing this packet
   */
  @Override
  public String toString() {
    return "PlayerCommand{"
        + "unsigned=" + unsigned
        + ", command='" + command + '\''
        + ", timestamp=" + timestamp
        + ", salt=" + salt
        + ", signedPreview=" + signedPreview
        + ", previousMessages=" + Arrays.toString(previousMessages)
        + ", arguments=" + arguments
        + '}';
  }

  /**
   * Handles this keyed player command packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates handling logic to {@code handler.handle(this)} to execute or verify
   * the command and its associated cryptographic data.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
