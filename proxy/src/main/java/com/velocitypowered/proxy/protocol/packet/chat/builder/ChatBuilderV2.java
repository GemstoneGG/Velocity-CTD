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

package com.velocitypowered.proxy.protocol.packet.chat.builder;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ChatType;
import com.velocitypowered.proxy.protocol.packet.chat.LastSeenMessages;
import java.time.Instant;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An abstract class for building chat components in version 2 of the chat system.
 *
 * <p>The {@code ChatBuilderV2} class provides the foundation for creating and formatting
 * chat components, allowing subclasses to implement specific behaviors for constructing
 * chat messages or text components.</p>
 */
public abstract class ChatBuilderV2 {

  /**
   * The protocol version this builder is targeting.
   */
  protected final ProtocolVersion version;

  /**
   * The rich Adventure component to display in chat.
   */
  protected @MonotonicNonNull Component component;

  /**
   * The original message string from the player, if any.
   */
  protected @MonotonicNonNull String message;

  /**
   * The player associated with this chat, if applicable.
   */
  protected @Nullable Player sender;

  /**
   * The Adventure identity to associate with the sender.
   */
  protected @Nullable Identity senderIdentity;

  /**
   * The timestamp representing when the message was sent.
   */
  protected Instant timestamp;

  /**
   * The type of chat message being built (e.g., system, chat, action bar).
   */
  protected ChatType type = ChatType.CHAT;

  /**
   * Optional list of last seen messages used for signed chat validation (1.19.3+).
   */
  protected @Nullable LastSeenMessages lastSeenMessages;

  /**
   * Constructs a new {@code ChatBuilderV2} instance with the given protocol version.
   *
   * @param version the protocol version used for packet construction
   */
  protected ChatBuilderV2(final ProtocolVersion version) {
    this.version = version;
    this.timestamp = Instant.now();
  }

  /**
   * Sets the {@link Component} that will be rendered as chat output.
   *
   * @param component the Adventure component
   * @return this builder instance
   */
  public ChatBuilderV2 component(final Component component) {
    this.component = component;
    return this;
  }

  /**
   * Sets the raw message string (used for original input or signing).
   *
   * @param message the unformatted message string
   * @return this builder instance
   */
  public ChatBuilderV2 message(final String message) {
    this.message = message;
    return this;
  }

  /**
   * Sets the {@link ChatType} for the outgoing message.
   *
   * @param chatType the message type (e.g., {@code CHAT}, {@code SYSTEM})
   * @return this builder instance
   */
  public ChatBuilderV2 setType(final ChatType chatType) {
    this.type = chatType;
    return this;
  }

  /**
   * Overrides the default timestamp with the specified {@link Instant}.
   *
   * @param timestamp the timestamp to apply
   * @return this builder instance
   */
  public ChatBuilderV2 setTimestamp(final Instant timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  /**
   * Associates the outgoing message with the given {@link Identity}.
   *
   * @param identity the sender identity
   * @return this builder instance
   */
  public ChatBuilderV2 forIdentity(final Identity identity) {
    this.senderIdentity = identity;
    return this;
  }

  /**
   * Marks this message as sent by a {@link Player}, used for context like signatures.
   *
   * @param player the player sending the message (nullable)
   * @return this builder instance
   */
  public ChatBuilderV2 asPlayer(final @Nullable Player player) {
    this.sender = player;
    return this;
  }

  /**
   * Marks this message as coming from the server itself (no player or identity).
   *
   * @return this builder instance
   */
  public ChatBuilderV2 asServer() {
    this.senderIdentity = null;
    return this;
  }

  /**
   * Attaches a {@link LastSeenMessages} structure used in signed chat filtering
   * for 1.19.3 and newer protocol versions.
   *
   * @param lastSeenMessages the set of acknowledged messages
   * @return this builder instance
   */
  public ChatBuilderV2 setLastSeenMessages(final LastSeenMessages lastSeenMessages) {
    this.lastSeenMessages = lastSeenMessages;
    return this;
  }

  /**
   * Constructs a client-bound {@link MinecraftPacket} based on the configured state.
   *
   * @return the packet to send to the client
   */
  public abstract MinecraftPacket toClient();

  /**
   * Constructs a server-bound {@link MinecraftPacket} based on the configured state.
   *
   * @return the packet to send to the server
   */
  public abstract MinecraftPacket toServer();
}
