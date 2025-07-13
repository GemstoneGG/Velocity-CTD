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

package com.velocitypowered.proxy.protocol.packet.chat.legacy;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a legacy chat packet used in older versions of Minecraft.
 *
 * <p>The {@code LegacyChatPacket} is responsible for holding and transmitting chat messages
 * in the format used by legacy versions of Minecraft. It implements {@link MinecraftPacket}
 * to ensure compatibility with the packet-handling system.</p>
 */
public class LegacyChatPacket implements MinecraftPacket {

  /**
   * Constant representing a normal player chat message.
   */
  public static final byte CHAT_TYPE = (byte) 0;

  /**
   * Constant representing a system message (e.g., console output).
   */
  public static final byte SYSTEM_TYPE = (byte) 1;

  /**
   * Constant representing a game info message (e.g., action bar).
   */
  public static final byte GAME_INFO_TYPE = (byte) 2;

  /**
   * Maximum allowed length for a message sent from client to server (pre-1.19).
   */
  public static final int MAX_SERVERBOUND_MESSAGE_LENGTH = 256;

  /**
   * UUID placeholder used when no sender is provided.
   */
  public static final UUID EMPTY_SENDER = new UUID(0, 0);

  /**
   * The message payload in JSON format or raw string, depending on version.
   */
  private @Nullable String message;

  /**
   * The type of chat message (chat/system/info). Only sent in 1.8+ clientbound.
   */
  private byte type;

  /**
   * The UUID of the original sender (1.16+ clientbound).
   */
  private @Nullable UUID sender;

  /**
   * Constructs an empty {@code LegacyChatPacket} for decoding.
   */
  public LegacyChatPacket() {
  }

  /**
   * Constructs a {@code LegacyChatPacket} with the given message, type, and sender.
   *
   * @param message the message text or JSON string
   * @param type the message type byte (chat/system/info)
   * @param sender the sender UUID (nullable)
   */
  public LegacyChatPacket(@Nullable final String message, final byte type, @Nullable final UUID sender) {
    this.message = message;
    this.type = type;
    this.sender = sender;
  }

  /**
   * Returns the message content.
   *
   * @return the chat message
   * @throws IllegalStateException if the message is not yet set
   */
  public String getMessage() {
    if (message == null) {
      throw new IllegalStateException("Message is not specified");
    }

    return message;
  }

  /**
   * Sets the chat message.
   *
   * @param message the message content
   */
  public void setMessage(@Nullable final String message) {
    this.message = message;
  }

  /**
   * Returns the message type.
   *
   * @return the type byte (0 = chat, 1 = system, 2 = game info)
   */
  public byte getType() {
    return type;
  }

  /**
   * Sets the message type.
   *
   * @param type the type byte to assign
   */
  public void setType(final byte type) {
    this.type = type;
  }

  /**
   * Returns the sender UUID (clientbound 1.16+).
   *
   * @return the sender UUID, or {@code null} if unset
   */
  public UUID getSenderUuid() {
    return sender;
  }

  /**
   * Sets the sender UUID.
   *
   * @param sender the sender UUID
   */
  public void setSenderUuid(final UUID sender) {
    this.sender = sender;
  }

  @Override
  public final String toString() {
    return "Chat{"
        + "message='" + message + '\''
        + ", type=" + type
        + ", sender=" + sender
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    message = ProtocolUtils.readString(buf, direction == ProtocolUtils.Direction.CLIENTBOUND
        ? 262144 : version.noLessThan(ProtocolVersion.MINECRAFT_1_11) ? 256 : 100);
    if (direction == ProtocolUtils.Direction.CLIENTBOUND && version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      type = buf.readByte();
      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
        sender = ProtocolUtils.readUuid(buf);
      }
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (message == null) {
      throw new IllegalStateException("Message is not specified");
    }

    ProtocolUtils.writeString(buf, message);
    if (direction == ProtocolUtils.Direction.CLIENTBOUND && version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      buf.writeByte(type);
      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
        ProtocolUtils.writeUuid(buf, sender == null ? EMPTY_SENDER : sender);
      }
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
