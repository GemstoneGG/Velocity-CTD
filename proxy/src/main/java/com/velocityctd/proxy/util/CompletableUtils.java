/*
 * Copyright (C) 2026 Velocity Contributors
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

package com.velocityctd.proxy.util;

import java.util.concurrent.CompletionException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class CompletableUtils {

  /**
   * Strips {@link CompletionException}s from the {@link Throwable} parameter if there are any.
   *
   * @param maybeCompletionException an exception, possibly an {@link CompletionException}
   * @return the first cause in the exception chain that is either {@code null} or not an {@link CompletionException}
   */
  public static @Nullable Throwable cause(final @NonNull Throwable maybeCompletionException) {
    Throwable throwable = maybeCompletionException;
    while (throwable instanceof CompletionException) {
      throwable = throwable.getCause();
    }

    return throwable;
  }
}
