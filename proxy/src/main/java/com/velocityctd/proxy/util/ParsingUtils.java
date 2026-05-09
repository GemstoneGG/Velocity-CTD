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

import java.util.function.Function;

public class ParsingUtils {

  /**
   * Replaces variables of the form <code>{name}</code> in the input string with values produced
   * by the given mapper.
   *
   * <p>Each variable is delimited by a literal <code>{</code> and <code>}</code>. The mapper is
   * called with the inner name only (no braces). If it returns a non-null value, that value is
   * substituted in place; if it returns {@code null}, the original <code>{name}</code> is
   * written back unchanged so unknown placeholders pass through intact. Text outside variables
   * is passed through unchanged. Nesting is not supported: a <code>{</code> inside a variable is
   * treated as part of the name. If the input ends while a variable is still open (no matching
   * <code>}</code>), the opening <code>{</code> and any partial content are written back
   * unchanged.
   *
   * @param input the string to process
   * @param variableMapper function mapping a variable name (without braces) to its replacement,
   *                       or {@code null} to leave the <code>{name}</code> literal in the output
   * @return the input with all known variables substituted
   */
  public static String parseVariables(String input, Function<String, String> variableMapper) {
    StringBuilder out = new StringBuilder(input.length());
    StringBuilder variable = new StringBuilder();
    boolean inVariable = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (!inVariable) {
        if (c == '{') {
          // start reading variable
          inVariable = true;
        } else {
          // pass-through output string
          out.append(c);
        }
      } else {
        if (c == '}') {
          // write variable value
          String value = variableMapper.apply(variable.toString());
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
}
