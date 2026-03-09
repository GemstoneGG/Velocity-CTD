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

package com.velocitypowered.proxy.protocol.util;

import com.google.common.base.Strings;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.handler.codec.CorruptedFrameException;

/**
 * Extends {@link com.google.common.base.Preconditions} for Netty's
 * {@link CorruptedFrameException}.
 */
public final class NettyPreconditions {

  /**
   * A generic {@link QuietDecoderException} used to signal that a packet was invalid or malformed.
   *
   * <p>This constant is used in decoding logic to throw a generic error when a packet
   * fails validation or structure checks. To diagnose the cause of this exception,
   * you can enable packet decode logging by launching Velocity with the system property
   * {@code -Dvelocity.packet-decode-logging=true}.</p>
   */
  private static final QuietDecoderException BAD = new QuietDecoderException(
      "Invalid packet received. Launch Velocity with -Dvelocity.packet-decode-logging=true "
          + "to see more.");

  private NettyPreconditions() {
    throw new AssertionError();
  }

  /**
   * Throws {@link CorruptedFrameException} if {@code b} is false.
   *
   * @param b       the expression to check
   * @param message the message to include in the thrown {@link CorruptedFrameException}
   */
  public static void checkFrame(final boolean b, final String message) {
    if (!b) {
      throw MinecraftDecoder.DEBUG ? new CorruptedFrameException(message) : BAD;
    }
  }

  /**
   * Throws {@link CorruptedFrameException} if {@code b} is false.
   *
   * @param b       the expression to check
   * @param message the message to include in the thrown {@link CorruptedFrameException}, formatted
   *                like {@link com.google.common.base.Preconditions#checkArgument(boolean)} and
   *                friends
   * @param arg1    the first argument to format the message with
   */
  public static void checkFrame(final boolean b, final String message, final Object arg1) {
    if (!b) {
      if (MinecraftDecoder.DEBUG) {
        throw new CorruptedFrameException(Strings.lenientFormat(message, arg1));
      } else {
        throw BAD;
      }
    }
  }

  /**
   * Throws {@link CorruptedFrameException} if {@code b} is false.
   *
   * @param b       the expression to check
   * @param message the message to include in the thrown {@link CorruptedFrameException}, formatted
   *                like {@link com.google.common.base.Preconditions#checkArgument(boolean)} and
   *                friends
   * @param arg1    the first argument to format the message with
   * @param arg2    the second argument to format the message with
   */
  public static void checkFrame(final boolean b, final String message, final Object arg1, final Object arg2) {
    if (!b) {
      if (MinecraftDecoder.DEBUG) {
        throw new CorruptedFrameException(Strings.lenientFormat(message, arg1, arg2));
      } else {
        throw BAD;
      }
    }
  }

  /**
   * Throws {@link CorruptedFrameException} if {@code b} is false.
   *
   * @param b       the expression to check
   * @param message the message to include in the thrown {@link CorruptedFrameException}, formatted
   *                like {@link com.google.common.base.Preconditions#checkArgument(boolean)} and
   *                friends
   * @param args    the arguments to format the message with-
   */
  public static void checkFrame(final boolean b, final String message, final Object... args) {
    if (!b) {
      if (MinecraftDecoder.DEBUG) {
        throw new CorruptedFrameException(Strings.lenientFormat(message, args));
      } else {
        throw BAD;
      }
    }
  }
}
