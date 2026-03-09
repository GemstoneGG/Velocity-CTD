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

  /**
   * Returns a string representation of this encryption response packet.
   *
   * <p>This includes the contents of the shared secret and verify token arrays.</p>
   *
   * @return a string describing this packet
   */
  @Override
  public String toString() {
    return "EncryptionResponse{"
        + "sharedSecret=" + Arrays.toString(sharedSecret)
        + ", verifyToken=" + Arrays.toString(verifyToken)
        + '}';
  }

  /**
   * Decodes this encryption response packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the encrypted shared secret, an optional salt (for Minecraft 1.19–1.19.2),
   * and the encrypted verify token using version-dependent encoding logic.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
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

  /**
   * Encodes this encryption response packet into the provided {@link ByteBuf}.
   *
   * <p>This writes the encrypted shared secret, the optional salt (1.19–1.19.2),
   * and the encrypted verify token depending on protocol version.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
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

  /**
   * Handles this encryption response packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates to {@code handler.handle(this)} to verify the encrypted data
   * and complete the login encryption handshake.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Computes a conservative upper bound (in bytes) for this encryption response packet
   * for the given protocol version.
   *
   * <p>The calculation accounts for:
   * <ul>
   *   <li>a fixed-size RSA shared secret (plus its 2-byte VarInt length prefix),</li>
   *   <li>a verify token (plus its 2-byte VarInt length prefix), whose maximum size
   *       increases on newer protocols, and</li>
   *   <li>version-dependent extras such as the left/right marker byte and salt.</li>
   * </ul>
   * The bound favors safety to help detect malformed or oversized packets during decode.</p>
   *
   * @param buf the input buffer (unused in this calculation)
   * @param direction the packet direction
   * @param version the Minecraft protocol version
   * @return an upper-bound estimate of the packet length in bytes
   */
  @Override
  public int decodeExpectedMaxLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
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

  /**
   * Computes a conservative lower bound (in bytes) for this encryption response packet
   * for the given protocol version.
   *
   * <p>This starts from the corresponding upper bound and subtracts the sizes of
   * version-dependent optional components (e.g., the extended verify token and salt)
   * where the protocol allows them to be omitted, yielding the smallest valid form
   * of the packet.</p>
   *
   * @param buf the input buffer (unused in this calculation)
   * @param direction the packet direction
   * @param version the Minecraft protocol version
   * @return a lower-bound estimate of the packet length in bytes
   */
  @Override
  public int decodeExpectedMinLength(final ByteBuf buf, final Direction direction, final ProtocolVersion version) {
    int base = decodeExpectedMaxLength(buf, direction, version);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      // These are "optional"
      base -= 128 + 8;
    }

    return base;
  }
}
