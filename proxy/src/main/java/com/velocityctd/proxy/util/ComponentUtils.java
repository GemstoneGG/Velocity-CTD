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

import gg.gemstone.component.ComponentParser;
import gg.gemstone.component.translator.MiniMessageTranslators;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.translation.GlobalTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for working with Adventure {@link net.kyori.adventure.text.Component}s.
 *
 * <p>User-supplied text is parsed through the {@code gg.gemstone:component} library, which
 * normalizes legacy {@code &}/{@code §} formatting codes and Mojang-style hex colors into
 * MiniMessage before deserialization.
 */
public final class ComponentUtils {

  /**
   * Shared {@link ComponentParser} configured to accept MiniMessage, Mojang-style hex colors
   * ({@code <&#RRGGBB>}, {@code &#RRGGBB}), bare hex colors ({@code #RRGGBB}) and both
   * {@code &} and {@code §} legacy formatting codes.
   */
  private static final ComponentParser PARSER = ComponentParser.builder()
      .withTranslators(
          MiniMessageTranslators.MOJANG_BOXED_HEX,
          MiniMessageTranslators.MOJANG_UNBOXED_HEX,
          MiniMessageTranslators.UNBOXED_HEX,
          MiniMessageTranslators.LEGACY_CODE_AMPERSAND,
          MiniMessageTranslators.LEGACY_CODE_SECTION)
      .withMiniMessage(MiniMessage.builder().strict(false).build())
      .build();

  private ComponentUtils() {
    throw new AssertionError("Instances of this class should not be created.");
  }

  /**
   * Returns the shared {@link ComponentParser} used to translate user-supplied text into
   * components.
   *
   * @return the shared parser
   */
  public static @NotNull ComponentParser parser() {
    return PARSER;
  }

  /**
   * Parses a user-supplied string into a {@link Component}, normalizing legacy formatting codes
   * and Mojang-style hex colors into MiniMessage.
   *
   * @param input the string to parse
   * @return the parsed component, or an empty component if {@code input} is {@code null}
   */
  public static @NotNull Component parse(@Nullable String input) {
    if (input == null) {
      return Component.empty();
    }
    return PARSER.parse(input);
  }

  /**
   * Returns whether the component tree contains the provided string in any text node.
   *
   * @param component the component to inspect
   * @param searchString the text to find
   * @return {@code true} if found, {@code false} otherwise
   */
  public static boolean containsString(@NotNull Component component,
                                       @NotNull String searchString) {
    return containsStringRecursive(GlobalTranslator.render(component, Locale.US), searchString);
  }

  private static boolean containsStringRecursive(@NotNull Component component,
                                                 @NotNull String searchString) {
    if (component instanceof TextComponent textComponent
        && textComponent.content().contains(searchString)) {
      return true;
    }

    if (component instanceof TranslatableComponent translatableComponent) {
      if (translatableComponent.key().contains(searchString)) {
        return true;
      }

      String fallback = translatableComponent.fallback();
      if (fallback != null && fallback.contains(searchString)) {
        return true;
      }

      for (TranslationArgument argument : translatableComponent.arguments()) {
        if (containsStringRecursive(argument.asComponent(), searchString)) {
          return true;
        }
      }
    }

    for (Component child : component.children()) {
      if (containsStringRecursive(child, searchString)) {
        return true;
      }
    }

    return false;
  }
}
