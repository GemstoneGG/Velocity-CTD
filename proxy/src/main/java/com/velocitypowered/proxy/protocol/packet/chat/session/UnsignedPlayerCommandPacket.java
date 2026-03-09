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

package com.velocitypowered.proxy.protocol.packet.chat.session;

import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.LastSeenMessages;
import io.netty.buffer.ByteBuf;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents an unsigned player command packet, extending {@link SessionPlayerCommandPacket}.
 *
 * <p>The {@code UnsignedPlayerCommandPacket} is used to handle player commands that are not
 * signed. It inherits session-specific behavior from {@link SessionPlayerCommandPacket}
 * while indicating that the command is unsigned.</p>
 */
public class UnsignedPlayerCommandPacket extends SessionPlayerCommandPacket {

  /**
   * Decodes this unsigned player command packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the raw command string sent by the player without any signing
   * or timestamp metadata.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    this.command = ProtocolUtils.readString(buf, ProtocolUtils.DEFAULT_MAX_STRING_SIZE);
  }

  /**
   * Encodes this unsigned player command packet into the given {@link ByteBuf}.
   *
   * <p>This writes only the raw command string. No signing or metadata is included.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeString(buf, this.command);
  }

  /**
   * Returns this same instance, as unsigned command packets do not track last-seen messages.
   *
   * <p>Signed command metadata is not applicable to unsigned command packets.</p>
   *
   * @param lastSeenMessages ignored
   * @return this {@code UnsignedPlayerCommandPacket} instance
   */
  @Override
  public SessionPlayerCommandPacket withLastSeenMessages(final @Nullable LastSeenMessages lastSeenMessages) {
    return this;
  }

  /**
   * Returns {@code false}, indicating the packet is not signed.
   *
   * @return {@code false}
   */
  public boolean isSigned() {
    return false;
  }

  /**
   * Returns {@link CommandExecuteEvent.SignedState#UNSIGNED} to indicate the
   * unsigned status of this command for event handling purposes.
   *
   * @return {@code UNSIGNED} signed state
   */
  @Override
  public CommandExecuteEvent.SignedState getEventSignedState() {
    return CommandExecuteEvent.SignedState.UNSIGNED;
  }

  /**
   * Returns a string representation of this unsigned player command packet.
   *
   * <p>This includes only the raw command string.</p>
   *
   * @return a string describing the packet
   */
  @Override
  public String toString() {
    return "UnsignedPlayerCommandPacket{"
        + "command='" + command + '\''
        + '}';
  }
}
