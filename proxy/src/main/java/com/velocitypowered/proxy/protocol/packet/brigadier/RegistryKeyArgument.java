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

  /**
   * Parses the input string as a registry key.
   *
   * <p>This method reads an unquoted string token from the input stream and returns it
   * as-is. No validation is performed on the string format.</p>
   *
   * @param stringReader the input reader positioned at the start of the key
   * @return the parsed registry key string
   * @throws CommandSyntaxException if the input is invalid or cannot be read
   */
  @Override
  public String parse(final StringReader stringReader) throws CommandSyntaxException {
    return stringReader.readString();
  }

  /**
   * Returns an empty {@link Suggestions} result, as this argument type does not support suggestions.
   *
   * @param context the command context
   * @param builder the suggestions builder
   * @return a {@link CompletableFuture} containing an empty suggestions list
   */
  @Override
  public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context,
                                                            final SuggestionsBuilder builder) {
    return Suggestions.empty();
  }

  /**
   * Returns a fixed set of example registry keys.
   *
   * <p>These examples are used for syntax help and client-side tab completion,
   * and demonstrate typical formats accepted by this argument type.</p>
   *
   * @return a collection of example input strings
   */
  @Override
  public Collection<String> getExamples() {
    return EXAMPLES;
  }
}
