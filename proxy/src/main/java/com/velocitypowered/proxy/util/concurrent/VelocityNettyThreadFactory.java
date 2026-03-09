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

package com.velocitypowered.proxy.util.concurrent;

import static com.google.common.base.Preconditions.checkNotNull;

import io.netty.util.concurrent.FastThreadLocalThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * Factory to create threads for the Netty event loop groups.
 */
public class VelocityNettyThreadFactory implements ThreadFactory {

  /**
   * Counter used to assign unique thread numbers.
   */
  private final AtomicInteger threadNumber = new AtomicInteger();

  /**
   * The name format used to name threads, passed to {@link String#format}.
   * Should include a single {@code %d} for the thread number.
   */
  private final String nameFormat;

  /**
   * Constructs a new {@code VelocityNettyThreadFactory} with the given thread name format.
   *
   * @param nameFormat the thread name format, must not be {@code null}
   */
  public VelocityNettyThreadFactory(final String nameFormat) {
    this.nameFormat = checkNotNull(nameFormat, "nameFormat");
  }

  /**
   * Creates a new {@link Thread} that wraps the provided {@link Runnable}.
   *
   * <p>The thread is named using the {@code nameFormat} provided to this factory,
   * using an incrementing thread number. The thread class used is
   * {@link FastThreadLocalThread}, which is optimized for Netty's internal
   * thread-local storage model.</p>
   *
   * @param r the {@link Runnable} to execute in the new thread
   * @return a newly created {@link Thread}
   */
  @Override
  public Thread newThread(final @NotNull Runnable r) {
    String name = String.format(nameFormat, threadNumber.getAndIncrement());
    return new FastThreadLocalThread(name) {
      @Override
      public void run() {
        r.run();
      }
    };
  }
}
