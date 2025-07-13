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

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents the packet sent from the client to the server during the login phase.
 * This packet contains the player's username, optionally a cryptographic key for
 * authentication, and the holder UUID depending on the Minecraft protocol version.
 */
public class ServerLoginPacket implements MinecraftPacket {

  /**
   * Thrown when a decoded {@code ServerLoginPacket} contains an empty username.
   *
   * <p>This exception is used to silently abort decoding without noisy logging.</p>
   */
  private static final QuietDecoderException EMPTY_USERNAME = new QuietDecoderException("Empty username!");

  /**
   * The username sent by the client.
   */
  private @Nullable String username;

  /**
   * The authenticated cryptographic player key sent by the client.
   * Only present in protocol versions 1.19 through 1.19.2.
   */
  private @Nullable IdentifiedKey playerKey;

  /**
   * The holder UUID representing the identity behind the signature key.
   * This is present in 1.19.1+ if the key signature has a holder,
   * and required as a field in 1.20.2+.
   */
  private @Nullable UUID holderUuid;

  /**
   * Constructs an empty {@code ServerLoginPacket}.
   *
   * <p>Fields must be manually populated before encoding.</p>
   */
  public ServerLoginPacket() {
  }

  /**
   * Constructs a {@code ServerLoginPacket} with a username and optional player key.
   *
   * @param username the player's username
   * @param playerKey the player's cryptographic key, or {@code null} if not present
   */
  public ServerLoginPacket(final String username, @Nullable final IdentifiedKey playerKey) {
    this.username = Preconditions.checkNotNull(username, "username");
    this.playerKey = playerKey;
  }

  /**
   * Constructs a new {@code ServerLoginPacket} with the specified username and holder UUID.
   *
   * @param username the player's username
   * @param holderUuid the holder UUID (optional)
   */
  public ServerLoginPacket(final String username, @Nullable final UUID holderUuid) {
    this.username = Preconditions.checkNotNull(username, "username");
    this.holderUuid = holderUuid;
    this.playerKey = null;
  }

  /**
   * Gets the player's username from the login packet.
   *
   * @return the player's username
   * @throws IllegalStateException if the username is not specified
   */
  public String getUsername() {
    if (username == null) {
      throw new IllegalStateException("No username found!");
    }

    return username;
  }

  /**
   * Gets the player's cryptographic key.
   *
   * @return the {@link IdentifiedKey}, or {@code null} if not present
   */
  public @Nullable IdentifiedKey getPlayerKey() {
    return this.playerKey;
  }

  /**
   * Sets the player's cryptographic key.
   *
   * @param playerKey the {@link IdentifiedKey}, or {@code null} to unset
   */
  public void setPlayerKey(@Nullable final IdentifiedKey playerKey) {
    this.playerKey = playerKey;
  }

  /**
   * Gets the holder UUID, which identifies the signer of the key if applicable.
   *
   * @return the UUID of the key-holder, or {@code null} if not set
   */
  public @Nullable UUID getHolderUuid() {
    return holderUuid;
  }

  @Override
  public final String toString() {
    return "ServerLogin{"
        + "username='" + username + '\''
        + "playerKey='" + playerKey + '\''
        + "holderUUID='" + holderUuid + '\''
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    username = ProtocolUtils.readString(buf, 16);
    if (username.isEmpty()) {
      throw EMPTY_USERNAME;
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
        playerKey = null;
      } else {
        if (buf.readBoolean()) {
          playerKey = ProtocolUtils.readPlayerKey(version, buf);
        } else {
          playerKey = null;
        }
      }

      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
        this.holderUuid = ProtocolUtils.readUuid(buf);
        return;
      }

      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
        if (buf.readBoolean()) {
          holderUuid = ProtocolUtils.readUuid(buf);
        }
      }
    } else {
      playerKey = null;
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (username == null) {
      throw new IllegalStateException("No username found!");
    }

    ProtocolUtils.writeString(buf, username);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      if (version.lessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
        if (playerKey != null) {
          buf.writeBoolean(true);
          ProtocolUtils.writePlayerKey(buf, playerKey);
        } else {
          buf.writeBoolean(false);
        }
      }

      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
        ProtocolUtils.writeUuid(buf, Objects.requireNonNull(this.holderUuid));
        return;
      }

      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
        if (playerKey != null && playerKey.getSignatureHolder() != null) {
          buf.writeBoolean(true);
          ProtocolUtils.writeUuid(buf, playerKey.getSignatureHolder());
        } else if (this.holderUuid != null) {
          buf.writeBoolean(true);
          ProtocolUtils.writeUuid(buf, this.holderUuid);
        } else {
          buf.writeBoolean(false);
        }
      }
    }
  }

  @Override
  public final int expectedMaxLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    // Accommodate the rare (but likely malicious) use of UTF-8 usernames, since it is technically
    // legal on the protocol level.
    int base = 1 + (16 * 3);
    // Adjustments for Key authentication
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      if (version.lessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
        // + 1 for the boolean present/ not present
        // + 8 for the long expiry
        // + 2 len for varint key size
        // + 294 for the key
        // + 2 len for varint signature size
        // + 512 for signature
        base += 1 + 8 + 2 + 294 + 2 + 512;
      }

      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
        // +1 boolean uuid optional
        // + 2 * 8 for the long msb/lsb
        base += 1 + 8 + 8;
      }
    }

    return base;
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
