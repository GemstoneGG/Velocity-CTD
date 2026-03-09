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

import java.util.Arrays;
import java.util.UUID;

/**
 * Represents a signer and a signature.
 */
public class SignaturePair {

  /**
   * The UUID of the signer who produced the signature.
   */
  private final UUID signer;

  /**
   * The raw cryptographic signature as a byte array.
   */
  private final byte[] signature;

  /**
   * Constructs a {@link SignaturePair} from the given signer UUID and signature.
   *
   * @param signer the UUID of the signer
   * @param signature the cryptographic signature
   */
  public SignaturePair(final UUID signer, final byte[] signature) {
    this.signer = signer;
    this.signature = signature;
  }

  /**
   * Returns the cryptographic signature value.
   *
   * @return the raw signature bytes
   */
  public byte[] getSignature() {
    return signature;
  }

  /**
   * Returns the UUID of the signer.
   *
   * @return the signer's UUID
   */
  public UUID getSigner() {
    return signer;
  }

  /**
   * Returns a string representation of this signature pair for debugging.
   *
   * @return a formatted string including the signer UUID and signature bytes
   */
  @Override
  public String toString() {
    return "SignaturePair{"
        + "signer=" + signer
        + ", signature=" + Arrays.toString(signature)
        + '}';
  }
}

