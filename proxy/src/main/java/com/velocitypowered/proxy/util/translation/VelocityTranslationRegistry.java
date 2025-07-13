/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

package com.velocitypowered.proxy.util.translation;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Velocity Translation Registry.
 * Based on <a href="https://github.com/KyoriPowered/adventure/pull/972">Adventure PR</a>.
 * MIT Licenced.
 */
public final class VelocityTranslationRegistry implements TranslationStore.StringBased<MessageFormat> {

  /**
   * The backing {@link TranslationStore.StringBased} instance that holds all registered
   * translations and performs locale-specific resolution.
   *
   * <p>This delegate is responsible for the actual translation logic and data storage,
   * while {@code VelocityTranslationRegistry} adds MiniMessage rendering and Adventure integration.</p>
   */
  private final TranslationStore.StringBased<MessageFormat> backedRegistry;

  /**
   * Constructs a new {@code VelocityTranslationRegistry} wrapping the given translation store.
   *
   * @param backed the delegate {@link TranslationStore.StringBased} instance
   */
  public VelocityTranslationRegistry(final TranslationStore.StringBased<MessageFormat> backed) {
    this.backedRegistry = backed;
  }

  @Override
  public boolean contains(@NotNull final String key) {
    return backedRegistry.contains(key);
  }

  @Override
  public boolean contains(@NotNull final String key, @NotNull final Locale locale) {
    return false;
  }

  @Override
  public @NotNull Key name() {
    return backedRegistry.name();
  }

  @Override
  public @Nullable MessageFormat translate(@NotNull final String key, @NotNull final Locale locale) {
    return null;
  }

  @Override
  public @Nullable Component translate(@NotNull final TranslatableComponent component, @NotNull final Locale locale) {
    final MessageFormat translationFormat = backedRegistry.translate(component.key(), locale);

    if (translationFormat == null) {
      return null;
    }

    final String miniMessageString = translationFormat.toPattern();

    final Component resultingComponent;

    if (component.arguments().isEmpty()) {
      resultingComponent = MiniMessage.miniMessage().deserialize(miniMessageString);
    } else {
      resultingComponent = MiniMessage.miniMessage().deserialize(miniMessageString,
          new ArgumentTag(component.arguments().stream().map(c -> GlobalTranslator.render(c.asComponent(), locale)).toList()));
    }

    if (component.children().isEmpty()) {
      return resultingComponent;
    } else {
      return resultingComponent.children(component.children());
    }
  }

  @Override
  public void defaultLocale(@NotNull final Locale locale) {
    backedRegistry.defaultLocale(locale);
  }

  @Override
  public void register(@NotNull final String key, @NotNull final Locale locale, @NotNull final MessageFormat format) {
    backedRegistry.register(key, locale, format);
  }

  @Override
  public void registerAll(@NotNull final Locale locale, @NotNull final Map<String, MessageFormat> translations) {
    backedRegistry.registerAll(locale, translations);
  }

  @Override
  public void registerAll(@NotNull final Locale locale, @NotNull final Set<String> keys, final Function<String, MessageFormat> function) {
    backedRegistry.registerAll(locale, keys, function);
  }

  @Override
  public void registerAll(@NotNull final Locale locale, @NotNull final Path path, final boolean escapeSingleQuotes) {
    backedRegistry.registerAll(locale, path, escapeSingleQuotes);
  }

  @Override
  public void registerAll(@NotNull final Locale locale, @NotNull final ResourceBundle bundle, final boolean escapeSingleQuotes) {
    backedRegistry.registerAll(locale, bundle, escapeSingleQuotes);
  }

  @Override
  public void unregister(@NotNull final String key) {
    backedRegistry.unregister(key);
  }

  private record ArgumentTag(List<? extends ComponentLike> argumentComponents) implements TagResolver {

    /**
     * The full tag name recognized by the resolver: {@code <argument:x>}.
     */
    private static final String NAME = "argument";

    /**
     * The short tag name alias recognized by the resolver: {@code <arg:x>}.
     */
    private static final String NAME_1 = "arg";

    private ArgumentTag(@NotNull final List<? extends ComponentLike> argumentComponents) {
      this.argumentComponents = Objects.requireNonNull(argumentComponents, "argumentComponents");
    }

    @Override
    public Tag resolve(@NotNull final String name,
                       @NotNull final ArgumentQueue arguments,
                       @NotNull final Context ctx) throws ParsingException {
      final int index = arguments.popOr("No argument number provided")
              .asInt().orElseThrow(() -> ctx.newException("Invalid argument number", arguments));

      if (index < 0 || index >= argumentComponents.size()) {
        throw ctx.newException("Invalid argument number", arguments);
      }

      return Tag.inserting(argumentComponents.get(index));
    }

    @Override
    public boolean has(@NotNull final String name) {
      return name.equals(NAME) || name.equals(NAME_1);
    }
  }
}
