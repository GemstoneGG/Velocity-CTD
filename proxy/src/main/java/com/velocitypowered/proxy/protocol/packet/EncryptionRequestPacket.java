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
import io.netty.buffer.ByteBuf;
import java.util.Arrays;

/**
 * Represents the encryption request packet in Minecraft, which is sent by the server
 * during the encryption handshake process. This packet is used to initiate secure
 * communication by providing the client with the server's public key and a verified token.
 * The client must respond with the encrypted shared secret and verify token.
 */
public class EncryptionRequestPacket implements MinecraftPacket {

  /**
   * The server ID used for legacy authentication. Typically an empty string in modern clients.
   */
  private String serverId = "";

  /**
   * The server's public key used to establish encrypted communication.
   */
  private byte[] publicKey = EMPTY_BYTE_ARRAY;

  /**
   * A token used to verify the legitimacy of the client's encryption response.
   */
  private byte[] verifyToken = EMPTY_BYTE_ARRAY;

  /**
   * Whether the client should authenticate with Mojang session servers.
   */
  private boolean shouldAuthenticate = true;

  /**
   * Retrieves a defensive copy of the server's public key.
   *
   * @return the public key as a byte array
   */
  public byte[] getPublicKey() {
    return publicKey.clone();
  }

  /**
   * Sets the server's public key.
   *
   * @param publicKey the public key to use
   */
  public void setPublicKey(final byte[] publicKey) {
    this.publicKey = publicKey.clone();
  }

  /**
   * Retrieves a defensive copy of the verification token.
   *
   * @return the verification token as a byte array
   */
  public byte[] getVerifyToken() {
    return verifyToken.clone();
  }

  /**
   * Sets the verification token.
   *
   * @param verifyToken the token used to verify client authenticity
   */
  public void setVerifyToken(final byte[] verifyToken) {
    this.verifyToken = verifyToken.clone();
  }

  @Override
  public final String toString() {
    return "EncryptionRequest{"
        + "publicKey=" + Arrays.toString(publicKey)
        + ", verifyToken=" + Arrays.toString(verifyToken)
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    this.serverId = ProtocolUtils.readString(buf, 20);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      publicKey = ProtocolUtils.readByteArray(buf, 256);
      verifyToken = ProtocolUtils.readByteArray(buf, 16);
      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
        shouldAuthenticate = buf.readBoolean();
      }
    } else {
      publicKey = ProtocolUtils.readByteArray17(buf);
      verifyToken = ProtocolUtils.readByteArray17(buf);
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    ProtocolUtils.writeString(buf, this.serverId);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      ProtocolUtils.writeByteArray(buf, publicKey);
      ProtocolUtils.writeByteArray(buf, verifyToken);
      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
        buf.writeBoolean(shouldAuthenticate);
      }
    } else {
      ProtocolUtils.writeByteArray17(publicKey, buf, false);
      ProtocolUtils.writeByteArray17(verifyToken, buf, false);
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
