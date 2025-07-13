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

package com.velocitypowered.natives.encryption;

import java.security.GeneralSecurityException;
import javax.crypto.SecretKey;

/**
 * A factory interface for {@link VelocityCipher}.
 */
public interface VelocityCipherFactory {

  /**
   * Creates a {@link VelocityCipher} configured for encryption using the given key.
   *
   * @param key the secret key used for encryption
   * @return a new {@link VelocityCipher} instance for encryption
   * @throws GeneralSecurityException if the cipher cannot be initialized
   */
  VelocityCipher forEncryption(SecretKey key) throws GeneralSecurityException;

  /**
   * Creates a {@link VelocityCipher} configured for decryption using the given key.
   *
   * @param key the secret key used for decryption
   * @return a new {@link VelocityCipher} instance for decryption
   * @throws GeneralSecurityException if the cipher cannot be initialized
   */
  VelocityCipher forDecryption(SecretKey key) throws GeneralSecurityException;
}
