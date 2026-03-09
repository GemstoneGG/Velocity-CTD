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
  public ServerLoginPacket(final String username, final @Nullable IdentifiedKey playerKey) {
    this.username = Preconditions.checkNotNull(username, "username");
    this.playerKey = playerKey;
  }

  /**
   * Constructs a new {@code ServerLoginPacket} with the specified username and holder UUID.
   *
   * @param username the player's username
   * @param holderUuid the holder UUID (optional)
   */
  public ServerLoginPacket(final String username, final @Nullable UUID holderUuid) {
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
  public void setPlayerKey(final @Nullable IdentifiedKey playerKey) {
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

  /**
   * Returns a string representation of this server login packet.
   *
   * <p>This includes the username, optional cryptographic key, and key holder UUID.</p>
   *
   * @return a string describing this packet
   */
  @Override
  public String toString() {
    return "ServerLogin{"
        + "username='" + username + '\''
        + "playerKey='" + playerKey + '\''
        + "holderUUID='" + holderUuid + '\''
        + '}';
  }

  /**
   * Decodes the server login packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the player's username and optionally the cryptographic key and UUID
   * of the key-holder, depending on the protocol version.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
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

  /**
   * Encodes this server login packet into the given {@link ByteBuf}.
   *
   * <p>This writes the player's username and any cryptographic key or holder UUID,
   * following protocol version-specific rules.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
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

  /**
   * Computes a conservative upper bound (in bytes) for the total size of this packet
   * when decoded from the network.
   *
   * <p>The estimate includes all fixed and optional fields across supported protocol
   * versions, such as:</p>
   *
   * <ul>
   *   <li>Base fields: username (UTF-8, up to 16 characters) and one boolean flag.</li>
   *   <li>Key authentication data (1.19+): presence flag, expiry timestamp, encoded public key,
   *       and optional signature block.</li>
   *   <li>Profile UUID fields (1.19.1+): optional UUID flag plus two 64-bit segments (MSB/LSB).</li>
   * </ul>
   *
   * <p>These calculations intentionally overestimate to account for variable-length
   * strings, multibyte VarInts, and potential UTF-8 expansion (up to 3 bytes per character),
   * as well as to safely handle malformed or maliciously large input.</p>
   *
   * @param buf the input buffer (unused in this calculation)
   * @param direction the packet direction (clientbound or serverbound)
   * @param version the Minecraft protocol version
   * @return a conservative upper-bound estimate of this packet’s byte length
   */
  @Override
  public int decodeExpectedMaxLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
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

  /**
   * Handles this server login packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates the packet processing to {@code handler.handle(this)}.</p>
   *
   * @param handler the session handler to process the packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
