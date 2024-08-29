/*
 * Copyright (C) 2021-2023 Velocity Contributors
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
public record SignaturePair(UUID signer, byte[] signature) {

  @Override
  public String toString() {
    return "SignaturePair{"
        + "signer=" + signer
        + ", signature=" + Arrays.toString(signature)
        + '}';
  }
}
