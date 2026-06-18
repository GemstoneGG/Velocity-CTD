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

package com.velocityctd.proxy.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class PlaceholderSubstitutor {

  private static final Logger LOGGER = LogManager.getLogger(PlaceholderSubstitutor.class);

  /**
   * Substitutes variables in each element of the input list, as described by
   * {@link #substitute(String, Resolver...)}. Each line is processed independently, so a
   * <code>{...}</code> variable never spans across list elements. A replacement value may itself
   * contain <code>\n</code>, in which case it is expanded into additional output lines.
   *
   * @param input the lines to process
   * @param resolvers resolve a variable name and its parsed arguments to a replacement,
   *                  or {@code null} to leave the literal in the output. first non-null return
   *                  value is used.
   * @return the input lines with all known variables substituted, with any replacement-introduced
   *         line breaks expanded into separate elements
   */
  public static @NonNull List<String> substitute(@NonNull List<String> input, @NonNull Resolver... resolvers) {
    List<String> output = new ArrayList<>(input.size());
    for (String line : input) {
      String substituted = substitute(line, resolvers);

      // a resolver may introduce '\n's, which expand into additional output lines
      int start = 0;
      int newline;
      while ((newline = substituted.indexOf('\n', start)) >= 0) {
        output.add(substituted.substring(start, newline));
        start = newline + 1;
      }
      output.add(substituted.substring(start));
    }

    return output;
  }

  /**
   * Replaces variables of the form <code>{name}</code> or <code>{name:key=value:...}</code> in the
   * input with values produced by the given mapper. Unknown variables and mapper exceptions leave
   * the original <code>{...}</code> literal intact. A <code>{</code>, <code>}</code>, <code>:</code>
   * or <code>=</code> can be included literally by escaping it with a backslash (e.g. <code>\{</code>,
   * <code>\}</code>, <code>\:</code>, <code>\=</code>); a literal backslash is written as <code>\\</code>.
   *
   * @param input the string to process
   * @param resolvers resolve a variable name and its parsed arguments to a replacement,
   *                  or {@code null} to leave the literal in the output. first non-null return
   *                  value is used.
   * @return the input with all known variables substituted
   */
  public static @NonNull String substitute(@NonNull String input, @NonNull Resolver... resolvers) {
    if (input.indexOf('{') < 0 && input.indexOf('\\') < 0) {
      // nothing to substitute or unescape; avoid allocating
      return input;
    }

    StringBuilder out = new StringBuilder(input.length());
    StringBuilder variable = new StringBuilder();
    boolean inVariable = false;
    boolean escaped = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (!inVariable) {
        if (escaped) {
          // inline-unescape literal outside variables (no later unescape pass)
          out.append(c);
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '{') {
          // start reading variable
          inVariable = true;
        } else {
          // pass-through output string
          out.append(c);
        }
      } else {
        if (escaped) {
          // keep the backslash so the leaf unescape can resolve it
          variable.append(c);
          escaped = false;
        } else if (c == '\\') {
          variable.append(c);
          escaped = true;
        } else if (c == '}') {
          String value = safeParseVariable(variable.toString(), resolvers);

          if (value == null) {
            // pass-through unknown variables as-is
            out.append('{');
            out.append(variable);
            out.append('}');
          } else {
            // write variable value
            out.append(value);
          }

          variable.setLength(0);
          inVariable = false;
        } else {
          // pass-through variable name
          variable.append(c);
        }
      }
    }

    if (inVariable) {
      // unclosed '{', pass through as-is
      out.append('{');
      out.append(variable);
    }

    return out.toString();
  }

  private static @Nullable String safeParseVariable(String rawVariable, Resolver... resolvers) {
    List<String> variableParts = splitUnescaped(rawVariable, ':');
    String variableName = unescape(variableParts.getFirst());
    Map<String, String> arguments = parseArguments(variableParts.subList(1, variableParts.size()));

    try {
      for (Resolver resolver : resolvers) {
        String resolved = resolver.resolve(variableName, arguments);
        if (resolved != null) {
          return resolved;
        }
      }
    } catch (Exception e) {
      LOGGER.error("Exception during variable parsing in '{}'.", rawVariable, e);
    }

    return null;
  }

  private static Map<String, String> parseArguments(List<String> rawArguments) {
    Map<String, String> arguments = HashMap.newHashMap(rawArguments.size());
    for (String rawArgument : rawArguments) {
      List<String> argumentParts = splitUnescaped(rawArgument, '=');
      if (argumentParts.size() != 2) {
        continue;
      }
      arguments.put(unescape(argumentParts.get(0)), unescape(argumentParts.get(1)));
    }

    return Collections.unmodifiableMap(arguments);
  }

  private static List<String> splitUnescaped(String input, char delimiter) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean escaped = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (escaped) {
        current.append(c);
        escaped = false;
      } else if (c == '\\') {
        current.append(c);
        escaped = true;
      } else if (c == delimiter) {
        parts.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }

    parts.add(current.toString());
    return parts;
  }

  private static String unescape(String input) {
    StringBuilder out = new StringBuilder(input.length());
    boolean escaped = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (!escaped && c == '\\') {
        escaped = true;
      } else {
        out.append(c);
        escaped = false;
      }
    }

    return out.toString();
  }

  @FunctionalInterface
  public interface Resolver {

    @Nullable String resolve(@NonNull String name, @NonNull Map<String, String> arguments);
  }
}
