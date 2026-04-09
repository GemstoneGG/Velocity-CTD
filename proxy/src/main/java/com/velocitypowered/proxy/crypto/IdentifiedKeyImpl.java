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

package com.velocitypowered.proxy.crypto;

import com.google.common.base.Objects;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents the contents of a {@link IdentifiedKey}.
 */
public class IdentifiedKeyImpl implements IdentifiedKey {

  private final Revision revision;

  private final PublicKey publicKey;

  private final byte[] signature;

  private final Instant expiryTemporal;

  private @MonotonicNonNull Boolean isSignatureValid;

  private @MonotonicNonNull UUID holder;

  /**
   * Constructs a new identified key using the revision, raw public key bytes, expiration time, and signature.
   *
   * @param revision the revision of the key format
   * @param keyBits the encoded key bytes
   * @param expiry the epoch milliseconds at which the key expires
   * @param signature the signature over the key
   */
  public IdentifiedKeyImpl(final Revision revision, final byte[] keyBits, final long expiry, final byte[] signature) {
    this(revision, EncryptionUtils.parseRsaPublicKey(keyBits), Instant.ofEpochMilli(expiry), signature);
  }

  /**
   * Constructs a new identified key using a {@link PublicKey}, expiration time, and signature.
   *
   * @param revision the revision of the key format
   * @param publicKey the public key
   * @param expiryTemporal the expiry time
   * @param signature the signature for the key
   */
  public IdentifiedKeyImpl(final Revision revision, final PublicKey publicKey,
                           final Instant expiryTemporal, final byte[] signature) {
    this.revision = revision;
    this.publicKey = publicKey;
    this.expiryTemporal = expiryTemporal;
    this.signature = signature;
  }

  @Override
  public PublicKey getSignedPublicKey() {
    return publicKey;
  }

  @Override
  public PublicKey getSigner() {
    return EncryptionUtils.getYggdrasilSessionKey();
  }

  @Override
  public Instant getExpiryTemporal() {
    return expiryTemporal;
  }

  @Override
  public byte[] getSignature() {
    return signature.clone();
  }

  @Override
  public @Nullable UUID getSignatureHolder() {
    return holder;
  }

  @Override
  public Revision getKeyRevision() {
    return revision;
  }

  /**
   * Attempts to assign a UUID as the holder of this key, verifying it if necessary.
   *
   * @param holder the UUID of the supposed key-holder
   * @return {@code true} if the assignment and validation succeeded, {@code false} otherwise
   */
  public boolean internalAddHolder(final UUID holder) {
    if (holder == null) {
      return false;
    }

    if (this.holder == null) {
      Boolean result = validateData(holder);
      if (result == null || !result) {
        return false;
      }

      isSignatureValid = true;
      this.holder = holder;
      return true;
    }

    return this.holder.equals(holder) && isSignatureValid();
  }

  @Override
  public boolean isSignatureValid() {
    if (isSignatureValid == null) {
      isSignatureValid = validateData(holder);
    }

    return isSignatureValid != null && isSignatureValid;
  }

  private Boolean validateData(final @Nullable UUID verify) {
    if (revision == Revision.GENERIC_V1) {
      String pemKey = EncryptionUtils.pemEncodeRsaKey(publicKey);
      long expires = expiryTemporal.toEpochMilli();
      byte[] toVerify = (expires + pemKey).getBytes(StandardCharsets.US_ASCII);
      return EncryptionUtils.verifySignature(
          EncryptionUtils.SHA1_WITH_RSA, EncryptionUtils.getYggdrasilSessionKey(), signature,
          toVerify);
    } else {
      if (verify == null) {
        return null;
      }

      byte[] keyBytes = publicKey.getEncoded();
      byte[] toVerify = new byte[keyBytes.length + 24]; // length long * 3
      ByteBuffer fixedDataSet = ByteBuffer.wrap(toVerify).order(ByteOrder.BIG_ENDIAN);
      fixedDataSet.putLong(verify.getMostSignificantBits());
      fixedDataSet.putLong(verify.getLeastSignificantBits());
      fixedDataSet.putLong(expiryTemporal.toEpochMilli());
      fixedDataSet.put(keyBytes);
      return EncryptionUtils.verifySignature(EncryptionUtils.SHA1_WITH_RSA, EncryptionUtils.getYggdrasilSessionKey(), signature, toVerify);
    }
  }

  @Override
  public boolean verifyDataSignature(final byte[] signature, final byte[]... toVerify) {
    try {
      return EncryptionUtils.verifySignature(EncryptionUtils.SHA256_WITH_RSA, publicKey, signature, toVerify);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  public String toString() {
    return "IdentifiedKeyImpl{"
        + "revision=" + revision
        + ", publicKey=" + publicKey
        + ", signature=" + Arrays.toString(signature)
        + ", expiryTemporal=" + expiryTemporal
        + ", isSignatureValid=" + isSignatureValid
        + ", holder=" + holder
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof final IdentifiedKey that)) {
      return false;
    }

    return Objects.equal(this.getSignedPublicKey(), that.getSignedPublicKey())
        && Objects.equal(this.getExpiryTemporal(), that.getExpiryTemporal())
        && Arrays.equals(this.getSignature(), that.getSignature())
        && Objects.equal(this.getSigner(), that.getSigner());
  }
}
