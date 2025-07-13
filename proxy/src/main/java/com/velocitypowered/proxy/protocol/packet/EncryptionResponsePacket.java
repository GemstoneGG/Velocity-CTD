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

import static com.velocitypowered.proxy.connection.VelocityConstants.EMPTY_BYTE_ARRAY;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents the encryption response packet in Minecraft, which is sent by the client
 * during the encryption handshake process. This packet contains the shared secret
 * and verifies the token used to establish secure communication between the client
 * and the server.
 *
 * <p>The packet structure varies depending on the Minecraft protocol version, with additional
 * fields such as a salt being present in versions 1.19 and above.</p>
 */
public class EncryptionResponsePacket implements MinecraftPacket {

  /**
   * Exception thrown when a salt value is expected in an encryption response packet
   * but is not present.
   *
   * <p>This typically occurs when handling clients using protocol versions 1.19 to 1.19.2
   * where the salt is conditionally included based on a boolean flag.</p>
   */
  private static final QuietDecoderException NO_SALT = new QuietDecoderException(
      "Encryption response didn't contain salt");

  /**
   * The shared secret key encrypted with the server's public key.
   * This is used to initialize the secure connection between client and server.
   */
  private byte[] sharedSecret = EMPTY_BYTE_ARRAY;

  /**
   * The verification token encrypted with the server's public key.
   * Used to verify that the client has the correct private key.
   */
  private byte[] verifyToken = EMPTY_BYTE_ARRAY;

  /**
   * Optional salt used in the encryption handshake (introduced in 1.19).
   * If present, indicates the handshake used the newer variant with salt and a boolean marker.
   */
  private @Nullable Long salt;

  /**
   * Returns a defensive copy of the encrypted shared secret sent by the client.
   *
   * @return the encrypted shared secret
   */
  public byte[] getSharedSecret() {
    return sharedSecret.clone();
  }

  /**
   * Returns a defensive copy of the encrypted verify token sent by the client.
   *
   * @return the encrypted verify token
   */
  public byte[] getVerifyToken() {
    return verifyToken.clone();
  }

  /**
   * Retrieves the salt used in the encryption response. The salt is introduced in
   * Minecraft version 1.19 and is optional in certain protocol versions.
   *
   * @return the salt used in the encryption response
   * @throws QuietDecoderException if the salt is not present
   */
  public long getSalt() {
    if (salt == null) {
      throw NO_SALT;
    }

    return salt;
  }

  @Override
  public final String toString() {
    return "EncryptionResponse{"
        + "sharedSecret=" + Arrays.toString(sharedSecret)
        + ", verifyToken=" + Arrays.toString(verifyToken)
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      this.sharedSecret = ProtocolUtils.readByteArray(buf, 128);

      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)
          && version.lessThan(ProtocolVersion.MINECRAFT_1_19_3)
          && !buf.readBoolean()) {
        salt = buf.readLong();
      }

      this.verifyToken = ProtocolUtils.readByteArray(buf,
          version.noLessThan(ProtocolVersion.MINECRAFT_1_19) ? 256 : 128);
    } else {
      this.sharedSecret = ProtocolUtils.readByteArray17(buf);
      this.verifyToken = ProtocolUtils.readByteArray17(buf);
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      ProtocolUtils.writeByteArray(buf, sharedSecret);
      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19) && version.lessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
        if (salt != null) {
          buf.writeBoolean(false);
          buf.writeLong(salt);
        } else {
          buf.writeBoolean(true);
        }
      }

      ProtocolUtils.writeByteArray(buf, verifyToken);
    } else {
      ProtocolUtils.writeByteArray17(sharedSecret, buf, false);
      ProtocolUtils.writeByteArray17(verifyToken, buf, false);
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public final int expectedMaxLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    // It turns out these come out to the same length, whether we're talking >=1.8 or not.
    // The length prefix always winds up being 2 bytes.
    int base = 256 + 2 + 2;
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
      return base + 128;
    }
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      // Verify token is twice as long on 1.19+
      // Additional 1 byte for the left <> right and 8 bytes for salt
      base += 128 + 8 + 1;
    }

    return base;
  }

  @Override
  public final int expectedMinLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    int base = expectedMaxLength(buf, direction, version);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      // These are "optional"
      base -= 128 + 8;
    }

    return base;
  }
}
