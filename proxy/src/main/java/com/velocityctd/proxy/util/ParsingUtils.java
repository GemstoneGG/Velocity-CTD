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
   * Replaces variables of the form {@code {name}} in the input string with values produced by
   * the given mapper.
   *
   * <p>Each variable is delimited by a literal {@code '{'} and {@code '}'}. The mapper is called
   * with the inner name only (no braces) and its return value is substituted in place. Text
   * outside variables is passed through unchanged. Nesting is not supported: a {@code '{'} inside
   * a variable is treated as part of the name. If the input ends while a variable is still open
   * (no matching {@code '}'}), the partial content is dropped.
   *
   * @param input the string to process
   * @param variableMapper function mapping a variable name (without braces) to its replacement
   * @return the input with all variables substituted
   */
  public static String parseVariables(String input, Function<String, String> variableMapper) {
    StringBuilder variable = null;
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (variable == null) {
        if (c == '{') {
          // start reading variable
          variable = new StringBuilder();
        } else {
          // pass-through output string
          out.append(c);
        }
      } else {
        if (c == '}') {
          // write variable value
          out.append(variableMapper.apply(variable.toString()));

          variable = null;
        } else {
          // pass-through variable name
          variable.append(c);
        }
      }
    }

    return out.toString();
  }
}
