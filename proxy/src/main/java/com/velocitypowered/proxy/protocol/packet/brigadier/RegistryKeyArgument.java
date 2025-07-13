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

package com.velocitypowered.proxy.protocol.packet.brigadier;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a Brigadier {@link ArgumentType} for registry keys, which are typically
 * namespaced resource locations (e.g., {@code minecraft:diamond_sword}).
 *
 * <p>This argument type reads an unquoted string from input and treats it as a raw registry
 * key. It does not validate the format or resolve the key against a known registry.</p>
 *
 * <p>Examples include simple strings, namespaced keys, or numeric-like identifiers.</p>
 */
public class RegistryKeyArgument implements ArgumentType<String> {

  /**
   * A set of example inputs commonly used for registry key arguments.
   */
  private static final List<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");

  /**
   * The internal identifier used to associate this argument with a specific registry context.
   */
  private final String identifier;

  /**
   * Constructs a new {@link RegistryKeyArgument} with the given identifier string.
   *
   * @param identifier a registry category or type name (e.g., {@code "minecraft:item"})
   */
  public RegistryKeyArgument(final String identifier) {
    this.identifier = identifier;
  }

  /**
   * Returns the internal identifier associated with this registry argument.
   *
   * @return the registry type identifier string
   */
  public String getIdentifier() {
    return identifier;
  }

  @Override
  public final String parse(final StringReader stringReader) throws CommandSyntaxException {
    return stringReader.readString();
  }

  @Override
  public final <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context,
                                                            final SuggestionsBuilder builder) {
    return Suggestions.empty();
  }

  @Override
  public final Collection<String> getExamples() {
    return EXAMPLES;
  }
}
