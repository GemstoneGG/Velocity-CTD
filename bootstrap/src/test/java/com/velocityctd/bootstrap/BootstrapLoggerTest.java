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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BootstrapLoggerTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;

  private void captureStreams() {
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
  }

  private String capturedOut() {
    return out.toString(StandardCharsets.UTF_8);
  }

  private String capturedErr() {
    return err.toString(StandardCharsets.UTF_8);
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
    BootstrapLogger.setTrace(false);
  }

  @Test
  void infoIsPrefixedAndWrittenToStandardOut() {
    captureStreams();
    BootstrapLogger.info("hello");
    assertEquals("[bootstrap] hello" + System.lineSeparator(), capturedOut());
    assertEquals("", capturedErr());
  }

  @Test
  void warningIsPrefixedAndWrittenToStandardErr() {
    captureStreams();
    BootstrapLogger.warn("careful");
    assertEquals("[bootstrap] WARN: careful" + System.lineSeparator(), capturedErr());
  }

  @Test
  void errorIsPrefixedAndWrittenToStandardError() {
    captureStreams();
    BootstrapLogger.error("boom");
    assertEquals("[bootstrap] ERROR: boom" + System.lineSeparator(), capturedErr());
    assertEquals("", capturedOut());
  }

  @Test
  void traceIsSuppressedByDefault() {
    captureStreams();
    BootstrapLogger.trace("noisy");
    assertEquals("", capturedOut());
  }

  @Test
  void traceIsEmittedOnceEnabled() {
    captureStreams();
    BootstrapLogger.setTrace(true);
    BootstrapLogger.trace("noisy");
    assertEquals("[bootstrap] noisy" + System.lineSeparator(), capturedOut());
  }
}
