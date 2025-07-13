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

package com.velocitypowered.natives;

/**
 * Thrown when we cannot set up a variant of a native library.
 */
public class NativeSetupException extends RuntimeException {

  /**
   * Creates a new {@link NativeSetupException} with no message or cause.
   */
  public NativeSetupException() {
  }

  /**
   * Creates a new {@link NativeSetupException} with the specified message.
   *
   * @param message the detail message
   */
  public NativeSetupException(final String message) {
    super(message);
  }

  /**
   * Creates a new {@link NativeSetupException} with the specified message and cause.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   */
  public NativeSetupException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a new {@link NativeSetupException} with the specified cause.
   *
   * @param cause the cause of the exception
   */
  public NativeSetupException(final Throwable cause) {
    super(cause);
  }

  /**
   * Creates a new {@link NativeSetupException} with full customization.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   * @param enableSuppression whether suppression is enabled
   * @param writableStackTrace whether the stack trace should be writable
   */
  public NativeSetupException(final String message, final Throwable cause, final boolean enableSuppression,
                              final boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
