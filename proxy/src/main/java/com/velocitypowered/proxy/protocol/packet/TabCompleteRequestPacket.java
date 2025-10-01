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

import static com.velocitypowered.api.network.ProtocolVersion.MINECRAFT_1_13;
import static com.velocitypowered.api.network.ProtocolVersion.MINECRAFT_1_8;
import static com.velocitypowered.api.network.ProtocolVersion.MINECRAFT_1_9;

import com.google.common.base.MoreObjects;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a packet sent by the client when a tab-completion request is initiated.
 */
public class TabCompleteRequestPacket implements MinecraftPacket {

  /**
   * The maximum allowed length of a tab-completion string in vanilla Minecraft.
   */
  private static final int VANILLA_MAX_TAB_COMPLETE_LEN = 2048;

  /**
   * The command string for which tab-completion is being requested.
   */
  private @Nullable String command;

  /**
   * The transaction ID associated with this tab-completion request.
   */
  private int transactionId;

  /**
   * Whether the client assumes the string is a command (introduced in 1.9).
   */
  private boolean assumeCommand;

  /**
   * Whether the request includes a block position (introduced in 1.8).
   */
  private boolean hasPosition;

  /**
   * The block position associated with the request, if {@code hasPosition} is true.
   */
  private long position;

  /**
   * Gets the command string to be completed.
   *
   * @return the command string
   * @throws IllegalStateException if the command is not set
   */
  public String getCommand() {
    if (command == null) {
      throw new IllegalStateException("Command is not specified");
    }

    return command;
  }

  /**
   * Sets the command to be completed.
   *
   * @param command the command string
   */
  public void setCommand(final @Nullable String command) {
    this.command = command;
  }

  /**
   * Returns whether the client assumes the string is a command.
   *
   * @return {@code true} if the string is assumed to be a command, otherwise {@code false}
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean isAssumeCommand() {
    return assumeCommand;
  }

  /**
   * Sets whether the client assumes the string is a command.
   *
   * @param assumeCommand {@code true} if the string is assumed to be a command
   */
  public void setAssumeCommand(final boolean assumeCommand) {
    this.assumeCommand = assumeCommand;
  }

  /**
   * Returns whether the tab-completion request includes a block position.
   *
   * @return {@code true} if a position is included, otherwise {@code false}
   */
  public boolean hasPosition() {
    return hasPosition;
  }

  /**
   * Sets whether the tab-completion request includes a block position.
   *
   * @param hasPosition {@code true} if a position should be included
   */
  public void setHasPosition(final boolean hasPosition) {
    this.hasPosition = hasPosition;
  }

  /**
   * Gets the block position associated with the request.
   *
   * @return the position as a long
   */
  public long getPosition() {
    return position;
  }

  /**
   * Sets the block position associated with the request.
   *
   * @param position the block position
   */
  public void setPosition(final long position) {
    this.position = position;
  }

  /**
   * Gets the transaction ID of the request.
   *
   * @return the transaction ID
   */
  public int getTransactionId() {
    return transactionId;
  }

  /**
   * Sets the transaction ID for this request.
   *
   * @param transactionId the transaction ID
   */
  public void setTransactionId(final int transactionId) {
    this.transactionId = transactionId;
  }

  /**
   * Returns a string representation of this tab-complete request packet.
   *
   * <p>This includes the command string, transaction ID, assume-command flag,
   * block position flag, and block position (if applicable).</p>
   *
   * @return a string describing the tab-complete request
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("command", command)
        .add("transactionId", transactionId)
        .add("assumeCommand", assumeCommand)
        .add("hasPosition", hasPosition)
        .add("position", position)
        .toString();
  }

  /**
   * Decodes this tab-complete request packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the command string and optionally a transaction ID, assume-command flag,
   * and block position based on the protocol version.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(MINECRAFT_1_13)) {
      this.transactionId = ProtocolUtils.readVarInt(buf);
      this.command = ProtocolUtils.readString(buf, VANILLA_MAX_TAB_COMPLETE_LEN);
    } else {
      this.command = ProtocolUtils.readString(buf, VANILLA_MAX_TAB_COMPLETE_LEN);
      if (version.noLessThan(MINECRAFT_1_9)) {
        this.assumeCommand = buf.readBoolean();
      }

      if (version.noLessThan(MINECRAFT_1_8)) {
        this.hasPosition = buf.readBoolean();
        if (hasPosition) {
          this.position = buf.readLong();
        }
      }
    }
  }

  /**
   * Encodes the packet data into the provided {@link ByteBuf}.
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (client to server or server to client)
   * @param version the protocol version in use
   * @throws IllegalStateException if the command is not specified
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (command == null) {
      throw new IllegalStateException("Command is not specified");
    }

    if (version.noLessThan(MINECRAFT_1_13)) {
      ProtocolUtils.writeVarInt(buf, transactionId);
      ProtocolUtils.writeString(buf, command);
    } else {
      ProtocolUtils.writeString(buf, command);
      if (version.noLessThan(MINECRAFT_1_9)) {
        buf.writeBoolean(assumeCommand);
      }

      if (version.noLessThan(MINECRAFT_1_8)) {
        buf.writeBoolean(hasPosition);
        if (hasPosition) {
          buf.writeLong(position);
        }
      }
    }
  }

  /**
   * Handles this tab-complete request packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates to {@code handler.handle(this)} to generate command suggestions
   * or forward the request to the backend server.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
