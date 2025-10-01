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

package com.velocitypowered.proxy.crypto;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.crypto.KeySigned;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a signed chat command issued by a player, including signature metadata
 * and cryptographic verification data for command validation and preview enforcement.
 *
 * <p>This implementation is used when chat signing is enabled and the proxy
 * verifies or forwards command signing information to backend servers.</p>
 *
 * @since Velocity 3.2.0
 */
public class SignedChatCommand implements KeySigned {

  /**
   * The full raw command string executed by the player.
   */
  private final String command;

  /**
   * The public key that was used to sign the command.
   */
  private final PublicKey signer;

  /**
   * The expiry time after which the command signature is considered invalid.
   */
  private final Instant expiry;

  /**
   * The cryptographic salt associated with this signed command.
   */
  private final byte[] salt;

  /**
   * The UUID of the sender who issued the command.
   */
  public final UUID sender;

  /**
   * Whether the command preview was signed by the client.
   */
  private final boolean isPreviewSigned;

  /**
   * A map of argument keys to their respective cryptographic signatures.
   */
  private final Map<String, byte[]> signatures;

  /**
   * An array of previous signature pairs from the command signing history.
   */
  private final SignaturePair[] previousSignatures;

  /**
   * The final signature pair in the command signing chain, if any.
   */
  private final @Nullable SignaturePair lastSignature;

  /**
   * Constructs a {@link SignedChatCommand} from the given command signature metadata.
   *
   * @param command the raw command text
   * @param signer the public key that signed the command
   * @param sender the UUID of the player who issued the command
   * @param expiry the expiration time of the signature
   * @param signature a map of signed arguments
   * @param salt the salt used in the signature
   * @param isPreviewSigned whether the preview was signed
   * @param previousSignatures any previously chained signature pairs
   * @param lastSignature the last known signature pair, if present
   */
  public SignedChatCommand(final String command, final PublicKey signer, final UUID sender,
                           final Instant expiry, final Map<String, byte[]> signature, final byte[] salt,
                           final boolean isPreviewSigned, final SignaturePair[] previousSignatures,
                           final @Nullable SignaturePair lastSignature) {
    this.command = Preconditions.checkNotNull(command);
    this.signer = Preconditions.checkNotNull(signer);
    this.sender = Preconditions.checkNotNull(sender);
    this.signatures = Preconditions.checkNotNull(signature);
    this.expiry = Preconditions.checkNotNull(expiry);
    this.salt = Preconditions.checkNotNull(salt);
    this.isPreviewSigned = isPreviewSigned;
    this.previousSignatures = previousSignatures;
    this.lastSignature = lastSignature;
  }

  /**
   * Gets the public key that signed this command.
   *
   * @return the signer's public key
   */
  @Override
  public PublicKey getSigner() {
    return signer;
  }

  /**
   * Gets the expiration time of this command signature.
   *
   * @return the expiration timestamp
   */
  @Override
  public Instant getExpiryTemporal() {
    return expiry;
  }

  /**
   * Gets the signature of this command.
   *
   * <p>This is not implemented and always returns {@code null}, since the signature is split across arguments.</p>
   *
   * @return {@code null}
   */
  @Override
  public byte @Nullable [] getSignature() {
    return null;
  }

  /**
   * Gets the salt used to sign this command.
   *
   * @return the cryptographic salt
   */
  @Override
  public byte[] getSalt() {
    return salt;
  }

  /**
   * Gets the full base command string issued by the sender.
   *
   * @return the command text
   */
  public String getBaseCommand() {
    return command;
  }

  /**
   * Gets the map of argument names to their cryptographic signatures.
   *
   * @return the signature map
   */
  public Map<String, byte[]> getSignatures() {
    return signatures;
  }

  /**
   * Returns whether the client signed the preview of this command.
   *
   * @return {@code true} if the preview was signed, {@code false} otherwise
   */
  public boolean isPreviewSigned() {
    return isPreviewSigned;
  }

  /**
   * Gets the final signature pair, if provided by the client.
   *
   * @return the last {@link SignaturePair}, or {@code null} if not present
   */
  public @Nullable SignaturePair getLastSignature() {
    return lastSignature;
  }

  /**
   * Gets all previously chained signature pairs sent by the client.
   *
   * @return the previous signature array
   */
  public SignaturePair[] getPreviousSignatures() {
    return previousSignatures;
  }
}
