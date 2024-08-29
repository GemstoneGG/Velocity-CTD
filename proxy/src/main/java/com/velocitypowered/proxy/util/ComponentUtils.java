/*
 * Copyright (C) 2018-2024 Velocity Contributors
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Serialize a component to a string.
 *
 * @author Elmar Blume
 */
public final class ComponentUtils {

  // MiniMessage default: <#FFFFFF>
  private static final Pattern BOXED_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]){6}>");     // <#FFFFFF>
  private static final Pattern BOXED_MOJANG_PATTERN = Pattern.compile("<&#([A-Fa-f0-9]){6}>"); // &#FFFFFF
  private static final Pattern UNBOXED_HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]){6}");     // <&#FFFFFF>
  private static final Pattern UNBOXED_MOJANG_PATTERN = Pattern.compile("&#([A-Fa-f0-9]){6}"); // #FFFFFF

  private static final List<Pattern> ODD_HEX_PATTERNS = Arrays.asList(
      BOXED_MOJANG_PATTERN,   // <&#FFFFFF>
      BOXED_HEX_PATTERN,      // <#FFFFFF>
      UNBOXED_MOJANG_PATTERN, // &#FFFFFF
      UNBOXED_HEX_PATTERN     // #FFFFFF
  ); // <!> order matters

  private static final List<Pattern> UNBOXED_PATTERNS = Arrays.asList(
      UNBOXED_HEX_PATTERN,
      UNBOXED_MOJANG_PATTERN
  );

  private static final List<Pattern> MOJANG_PATTERNS = Arrays.asList(
      BOXED_MOJANG_PATTERN,
      UNBOXED_MOJANG_PATTERN
  );

  private static final MiniMessage MINI = MiniMessage.builder()
          .strict(false)
          .build();

  private static final Map<String, String> COLOR_MAP = new HashMap<>();

  static {
    COLOR_MAP.put("§", "&");
    COLOR_MAP.put("&0", "<reset><black>");
    COLOR_MAP.put("&1", "<reset><dark_blue>");
    COLOR_MAP.put("&2", "<reset><dark_green>");
    COLOR_MAP.put("&3", "<reset><dark_aqua>");
    COLOR_MAP.put("&4", "<reset><dark_red>");
    COLOR_MAP.put("&5", "<reset><dark_purple>");
    COLOR_MAP.put("&6", "<reset><gold>");
    COLOR_MAP.put("&7", "<reset><gray>");
    COLOR_MAP.put("&8", "<reset><dark_gray>");
    COLOR_MAP.put("&9", "<reset><blue>");
    COLOR_MAP.put("&a", "<reset><green>");
    COLOR_MAP.put("&b", "<reset><aqua>");
    COLOR_MAP.put("&c", "<reset><red>");
    COLOR_MAP.put("&d", "<reset><light_purple>");
    COLOR_MAP.put("&e", "<reset><yellow>");
    COLOR_MAP.put("&f", "<reset><white>");
    COLOR_MAP.put("&k", "<obfuscated>");
    COLOR_MAP.put("&l", "<bold>");
    COLOR_MAP.put("&m", "<strikethrough>");
    COLOR_MAP.put("&n", "<underlined>");
    COLOR_MAP.put("&o", "<italic>");
    COLOR_MAP.put("&r", "<reset>");
    COLOR_MAP.put("\\n", "<newline>");
  }

  private ComponentUtils() {
    throw new AssertionError("Instances of this class should not be created.");
  }

  /**
   * Serialize a component to a string.
   *
   * @param component the component to serialize
   * @return the serialized component
   */
  public static @NotNull String serializeComponent(Component component) {
    return MINI.serialize(component);
  }

  /**
   * Parses a string to a component.
   *
   * @param input the string to parse
   * @return the parsed component
   */
  public static @NotNull Component parseComponent(String input) {
    return MINI.deserialize(colorifyLegacy(input));
  }

  /**
   * Colorify component parsing hex patterns.
   *
   * @param input the string to colorify into a component
   * @return the colorized component
   */
  public static @NotNull Component colorify(String input) {
    if (input == null) {
      return Component.empty();
    }

    String parsedStr = input;

    // Parse the hex patterns
    for (final Pattern pattern : ODD_HEX_PATTERNS) {
      parsedStr = colorMatcher(parsedStr, pattern, UNBOXED_PATTERNS.contains(pattern));
    }
    return parseComponent(parsedStr.replace("D#DONE", "#"));
  }

  /**
   * Colorify legacy parsing legacy color codes.
   *
   * @param input the string to colorify into a component
   * @return the colorized component
   */
  public static String colorifyLegacy(String input) {
    String parsedStr = input;

    for (Map.Entry<String, String> entry : COLOR_MAP.entrySet()) {
      parsedStr = parsedStr.replace(entry.getKey(), entry.getValue());
    }
    return parsedStr;
  }

  /**
   * Strips matching hex patterns from a string.
   *
   * @param input the input
   * @return the string
   */
  public static String stripHex(String input) {
    for (Pattern pattern : ODD_HEX_PATTERNS) {
      input = pattern.matcher(input).replaceAll("");
    }
    return input;
  }

  private static @NotNull String colorMatcher(@NotNull String literal, @NotNull Pattern pattern, boolean unboxed) {
    final Matcher matcher = pattern.matcher(literal);

    while (matcher.find()) {
      final String matched = matcher.group();
      boolean requiresBoxing = false;

      if (unboxed) {
        final int literalIndex = literal.indexOf(matched);
        final int afterLiteralIndex = literalIndex + matched.length();

        if (literal.length() >= afterLiteralIndex) {
          final char charAt = literal.charAt(afterLiteralIndex);

          if (charAt != ':' && charAt != '>') {
            requiresBoxing = true;
          }
        }
      }

      final int index = matched.indexOf("#");
      final String hexCode = matched.substring(index + 1, index + 7);

      if (!requiresBoxing) {
        final String start;
        final String end = matched.substring(index + 7);
        if (MOJANG_PATTERNS.contains(pattern)) {
          start = matched.substring(0, index).replace("&", "");
        } else {
          start = matched.substring(0, index);
        }
        literal = literal.replace(matched, start + "D#DONE" + hexCode + end);
      } else {
        literal = literal.replace(matched, "<D#DONE" + hexCode + ">");
      }
    }
    return literal;
  }

  /**
   * Normalize any hex pattern to a standard hex pattern.
   *
   * @param hex the hex pattern to normalize
   * @return the normalized hex pattern
   */
  private static @NotNull String normalizeHex(@NotNull String hex) {
    if (hex.startsWith("<") || hex.startsWith("{")) {
      return hex.substring(1, hex.length() - 1);
    } else if (hex.startsWith("&")) {
      return hex.substring(1);
    } else {
      return hex;
    }
  }
}
