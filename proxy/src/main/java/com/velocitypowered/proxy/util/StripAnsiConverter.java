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

package com.velocitypowered.proxy.util;

import java.util.List;
import java.util.regex.Pattern;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;

/**
 * Strip Format Converter.
 * Based on <a href="https://github.com/PaperMC/Paper/pull/9313/files#diff-6c1396d60730e7053f0b761bdb487752467c9bc0444a4aac41908376b38a56bdR198-R233">Paper's patch</a>
 */
@Plugin(name = "stripAnsi", category = PatternConverter.CATEGORY)
@ConverterKeys("stripAnsi")
public class StripAnsiConverter extends LogEventPatternConverter {

  /**
   * Pattern to match ANSI escape codes used for coloring or formatting.
   */
  private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");

  /**
   * List of {@link PatternFormatter}s used to format the log event before
   * stripping ANSI escape codes.
   */
  private final List<PatternFormatter> formatters;

  /**
   * Constructs a new {@code StripAnsiConverter}.
   *
   * @param formatters the formatters that produce the original message content
   */
  protected StripAnsiConverter(final List<PatternFormatter> formatters) {
    super("stripAnsi", null);
    this.formatters = formatters;
  }

  /**
   * Formats the given {@link LogEvent}, strips ANSI escape codes,
   * and appends the sanitized message to the provided {@link StringBuilder}.
   *
   * @param event the log event to format
   * @param toAppendTo the buffer to append the formatted message to
   */
  @Override
  public void format(final LogEvent event, final StringBuilder toAppendTo) {
    int start = toAppendTo.length();
    for (final PatternFormatter formatter : formatters) {
      formatter.format(event, toAppendTo);
    }

    String content = toAppendTo.substring(start);
    content = ANSI_PATTERN.matcher(content).replaceAll("");

    toAppendTo.setLength(start);
    toAppendTo.append(content);
  }

  /**
   * Creates a new instance of {@code StripAnsiConverter}.
   *
   * @param config  the current Log4j configuration
   * @param options the options passed in the log4j configuration file
   * @return a new {@link StripAnsiConverter} instance, or {@code null}
   *         if the provided options are invalid
   */
  public static StripAnsiConverter newInstance(final Configuration config, final String[] options) {
    if (options.length != 1) {
      LOGGER.error("Incorrect number of options on stripFormat. Expected 1 received {}",
          options.length);
      return null;
    }

    PatternParser parser = PatternLayout.createPatternParser(config);
    List<PatternFormatter> formatters = parser.parse(options[0]);
    return new StripAnsiConverter(formatters);
  }
}
