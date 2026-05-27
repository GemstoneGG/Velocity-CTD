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

package com.velocitypowered.proxy.connection.player.resourcepack;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.util.NettyPreconditions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for serializing and deserializing the signed cookie data that carries applied
 * resource pack state across a {@code transferToHost} hand-off.
 */
public final class ResourcePackTransfer {

  public static final Key APPLIED_RESOURCE_PACKS_KEY = Key.key("velocity", "applied_resource_packs");

  private static final String ALGORITHM = "HmacSHA256";
  private static final ResourcePackInfo.Origin[] ORIGINS = ResourcePackInfo.Origin.values();
  private static final int SIGNATURE_LENGTH = 32;
  private static final int MAX_APPLIED_PACKS = 256;

  private ResourcePackTransfer() {
    throw new AssertionError();
  }

  /**
   * Cookie payload carrying state preserved across a {@code transferToHost}.
   *
   * @param appliedPacks the resource packs the player had applied at the moment of transfer
   */
  public record TransferSession(Collection<ResourcePackInfo> appliedPacks) {
  }

  /**
   * Serializes {@code session} into a signed byte array suitable for sending as a
   * {@link Key#key(String, String) velocity:applied_resource_packs} cookie. Returns
   * {@code null} when the session has no applied packs, in which case there is nothing
   * worth carrying across the transfer.
   *
   * @param secret  the HMAC key used to sign the payload (see {@link TransferPackSecret})
   * @param session the transfer-session state to encode
   * @return signed cookie payload, or {@code null} if no cookie should be sent
   */
  public static byte @Nullable [] createCookieData(byte[] secret, TransferSession session) {
    Collection<ResourcePackInfo> appliedResourcePacks = session.appliedPacks();
    if (appliedResourcePacks.isEmpty()) {
      return null;
    }

    ByteBuf buffer = Unpooled.buffer(appliedResourcePacks.size() * 256);
    try {
      ProtocolUtils.writeVarInt(buffer, appliedResourcePacks.size());

      for (ResourcePackInfo appliedResourcePack : appliedResourcePacks) {
        ProtocolUtils.writeString(buffer, appliedResourcePack.getUrl());
        ProtocolUtils.writeUuid(buffer, appliedResourcePack.getId());

        byte[] hash = appliedResourcePack.getHash();
        buffer.writeBoolean(hash != null);
        if (hash != null) {
          Preconditions.checkArgument(hash.length == 20, "Hash length is not 20");
          buffer.writeBytes(hash);
        }

        buffer.writeBoolean(appliedResourcePack.getShouldForce());
        Component prompt = appliedResourcePack.getPrompt();
        buffer.writeBoolean(prompt != null);
        if (prompt != null) {
          ProtocolUtils.writeString(buffer,
              ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MAXIMUM_VERSION).serialize(prompt));
        }

        ProtocolUtils.writeVarInt(buffer, appliedResourcePack.getOrigin().ordinal());
        ProtocolUtils.writeVarInt(buffer, appliedResourcePack.getOriginalOrigin().ordinal());
      }

      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      mac.update(buffer.array(), buffer.arrayOffset(), buffer.readableBytes());
      buffer.writeBytes(mac.doFinal());

      return Arrays.copyOfRange(buffer.array(), buffer.arrayOffset(),
          buffer.arrayOffset() + buffer.readableBytes());
    } catch (InvalidKeyException e) {
      throw new RuntimeException("Unable to sign applied resource packs cookie data", e);
    } catch (NoSuchAlgorithmException e) {
      // Should never happen — HmacSHA256 is mandated by the JDK.
      throw new AssertionError(e);
    } finally {
      buffer.release();
    }
  }

  /**
   * Verifies the HMAC signature on {@code data} and decodes the payload into a
   * {@link TransferSession}. Returns a session with an empty applied-packs collection when
   * {@code data} is {@code null} or empty (i.e. the previous proxy had no packs to carry).
   *
   * @param secret the HMAC key used to verify the payload (see {@link TransferPackSecret})
   * @param data   the signed cookie payload, or {@code null}/empty if no cookie was supplied
   * @return decoded transfer session
   * @throws SignatureException if the payload is shorter than the signature, the signature
   *                            does not match the body, or the secret key cannot be used
   * @throws DecoderException   if the payload is structurally malformed
   */
  public static TransferSession decodeAndValidateCookieData(byte[] secret, byte[] data)
      throws SignatureException, DecoderException {
    if (data == null || data.length == 0) {
      return new TransferSession(Collections.emptyList());
    }

    if (data.length <= SIGNATURE_LENGTH) {
      throw new SignatureException("Applied resource packs cookie data has no or incomplete signature");
    }

    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      mac.update(data, 0, data.length - SIGNATURE_LENGTH);

      if (!Arrays.equals(mac.doFinal(), 0, SIGNATURE_LENGTH,
          data, data.length - SIGNATURE_LENGTH, data.length)) {
        throw new SignatureException("Applied resource packs cookie data has invalid signature");
      }
    } catch (InvalidKeyException e) {
      throw new RuntimeException("Unable to verify signature of applied resource packs cookie data", e);
    } catch (NoSuchAlgorithmException e) {
      // Should never happen — HmacSHA256 is mandated by the JDK.
      throw new AssertionError(e);
    }

    ByteBuf buffer = Unpooled.wrappedBuffer(data);
    try {
      int size = ProtocolUtils.readVarInt(buffer);
      NettyPreconditions.checkFrame(size >= 0 && size <= MAX_APPLIED_PACKS,
          "Invalid applied pack count (got %s, maximum is %s)", size, MAX_APPLIED_PACKS);
      List<ResourcePackInfo> appliedResourcePacks = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        VelocityResourcePackInfo.BuilderImpl builder =
            new VelocityResourcePackInfo.BuilderImpl(ProtocolUtils.readString(buffer));
        builder.setId(ProtocolUtils.readUuid(buffer));
        if (buffer.readBoolean()) {
          byte[] hash = new byte[20];
          buffer.readBytes(hash);
          builder.setHash(hash);
        }

        builder.setShouldForce(buffer.readBoolean());
        if (buffer.readBoolean()) {
          builder.setPrompt(ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MAXIMUM_VERSION)
              .deserialize(ProtocolUtils.readString(buffer)));
        }

        builder.setOrigin(ORIGINS[ProtocolUtils.readVarInt(buffer)]);
        VelocityResourcePackInfo appliedResourcePack = builder.build();
        appliedResourcePack.setOriginalOrigin(ORIGINS[ProtocolUtils.readVarInt(buffer)]);
        appliedResourcePacks.add(appliedResourcePack);
      }

      return new TransferSession(appliedResourcePacks);
    } finally {
      buffer.release();
    }
  }
}
