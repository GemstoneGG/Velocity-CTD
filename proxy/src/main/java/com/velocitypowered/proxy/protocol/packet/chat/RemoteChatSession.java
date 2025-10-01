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

package com.velocitypowered.proxy.protocol.packet.chat;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.player.ChatSession;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a remote chat session that implements the {@link ChatSession} interface.
 * This session is used for handling chat interactions that occur remotely, typically between
 * a client and server, allowing for communication and session tracking.
 */
public class RemoteChatSession implements ChatSession {

  /**
   * The unique session ID used to identify this chat session.
   *
   * <p>This field may be {@code null} depending on protocol version or session state.</p>
   */
  private final @Nullable UUID sessionId;

  /**
   * The {@link IdentifiedKey} used to sign messages or associate with player identity.
   */
  private final IdentifiedKey identifiedKey;

  /**
   * Constructs a {@code RemoteChatSession} by reading from a {@link ByteBuf}, using the
   * protocol version to determine how to decode the player key.
   *
   * @param version the protocol version for compatibility
   * @param buf the buffer to decode from
   */
  public RemoteChatSession(final ProtocolVersion version, final ByteBuf buf) {
    this.sessionId = ProtocolUtils.readUuid(buf);
    this.identifiedKey = ProtocolUtils.readPlayerKey(version, buf);
  }

  /**
   * Constructs a {@code RemoteChatSession} using a known session ID and player key.
   *
   * @param sessionId the chat session UUID (nullable)
   * @param identifiedKey the {@link IdentifiedKey} associated with the session
   */
  public RemoteChatSession(final @Nullable UUID sessionId, final IdentifiedKey identifiedKey) {
    this.sessionId = sessionId;
    this.identifiedKey = identifiedKey;
  }

  /**
   * Returns the player's {@link IdentifiedKey}, which may be used for verifying message signatures.
   *
   * @return the identified key for the session
   */
  public IdentifiedKey getIdentifiedKey() {
    return identifiedKey;
  }

  /**
   * Returns the session's UUID, or {@code null} if unavailable.
   *
   * @return the session ID
   */
  public @Nullable UUID getSessionId() {
    return sessionId;
  }

  /**
   * Writes this session's data to the provided {@link ByteBuf}, including session ID and key.
   *
   * @param buf the buffer to write to
   */
  public void write(final ByteBuf buf) {
    ProtocolUtils.writeUuid(buf, Objects.requireNonNull(this.sessionId));
    ProtocolUtils.writePlayerKey(buf, this.identifiedKey);
  }
}
