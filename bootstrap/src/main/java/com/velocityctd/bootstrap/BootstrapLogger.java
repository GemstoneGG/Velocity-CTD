/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.bootstrap;

/**
 * Minimal console logger used before the proxy (and its real logging framework) has been started.
 */
public final class BootstrapLogger {

  private static final String PREFIX = "[bootstrap] ";
  private static final String TRACE_PREFIX = PREFIX;
  private static final String INFO_PREFIX = PREFIX;
  private static final String WARN_PREFIX = PREFIX + "WARN: ";
  private static final String ERROR_PREFIX = PREFIX + "ERROR: ";

  private static boolean trace = false;

  private BootstrapLogger() {
    throw new IllegalStateException("BootstrapLogger should not be instantiated.");
  }

  /**
   * Logs at a trace level (only logs when {@link #setTrace} was set to {@code true}).
   *
   * @param message the trace message to log
   */
  public static void trace(String message) {
    if (trace) {
      System.out.println(TRACE_PREFIX + message);
    }
  }

  /**
   * Logs at an info level.
   *
   * @param message the info message to log
   */
  public static void info(String message) {
    System.out.println(INFO_PREFIX + message);
  }

  /**
   * Logs at a warning level.
   *
   * @param message the warning message to log
   */
  public static void warn(String message) {
    System.err.println(WARN_PREFIX + message);
  }

  /**
   * Logs at an error level.
   *
   * @param message the error message to log
   */
  public static void error(String message) {
    System.err.println(ERROR_PREFIX + message);
  }

  /**
   * Sets whether trace logs (logged with {@link #trace(String)}) should be visible.
   *
   * @param trace whether to show trace logs or not
   */
  static void setTrace(boolean trace) {
    BootstrapLogger.trace = trace;
  }
}
