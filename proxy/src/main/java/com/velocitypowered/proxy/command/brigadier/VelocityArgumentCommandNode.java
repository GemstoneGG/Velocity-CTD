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

package com.velocitypowered.proxy.command.brigadier;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * An argument node that uses the given (possibly custom) {@link ArgumentType} for parsing, while
 * maintaining compatibility with the vanilla client. The argument type must be greedy and accept
 * any input.
 *
 * @param <S> the type of the command source
 * @param <T> the type of the argument to parse
 */
public class VelocityArgumentCommandNode<S, T> extends ArgumentCommandNode<S, String> {

  /**
   * The actual {@link ArgumentType} used for parsing, distinct from what is exposed to the client.
   */
  private final ArgumentType<T> type;

  VelocityArgumentCommandNode(final String name, final ArgumentType<T> type, final Command<S> command,
                              final Predicate<S> requirement,
                              final BiPredicate<CommandContextBuilder<S>, ImmutableStringReader> contextRequirement,
                              final CommandNode<S> redirect, final RedirectModifier<S> modifier, final boolean forks,
                              final SuggestionProvider<S> customSuggestions) {
    super(name, StringArgumentType.greedyString(), command, requirement, contextRequirement, redirect, modifier, forks, customSuggestions);
    this.type = Preconditions.checkNotNull(type, "type");
  }

  /**
   * Parses the input using the internal {@link ArgumentType}, instead of the client-facing one.
   *
   * @param reader the string reader to consume
   * @param contextBuilder the command context being built
   * @throws CommandSyntaxException if parsing fails
   */
  @Override
  public void parse(final StringReader reader, final CommandContextBuilder<S> contextBuilder) throws CommandSyntaxException {
    // Same as "super", except we use the rich ArgumentType
    final int start = reader.getCursor();
    final T result = this.type.parse(reader);
    if (reader.canRead()) {
      throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException()
          .createWithContext(reader, "Expected greedy ArgumentType to parse all input");
    }

    final ParsedArgument<S, T> parsed = new ParsedArgument<>(start, reader.getCursor(), result);
    contextBuilder.withArgument(getName(), parsed);
    contextBuilder.withNode(this, parsed.getRange());
  }

  /**
   * Provides command suggestions if a custom suggestion provider is available.
   *
   * @param context the command context
   * @param builder the suggestions builder
   * @return a future containing suggestions or an empty result
   * @throws CommandSyntaxException if suggestion generation fails
   */
  @Override
  public CompletableFuture<Suggestions> listSuggestions(
      final CommandContext<S> context, final SuggestionsBuilder builder)
      throws CommandSyntaxException {
    if (getCustomSuggestions() == null) {
      return Suggestions.empty();
    }

    return getCustomSuggestions().getSuggestions(context, builder);
  }

  /**
   * Not supported for this node type. Always throws.
   *
   * @return nothing, always throws
   * @throws UnsupportedOperationException always
   */
  @Override
  public RequiredArgumentBuilder<S, String> createBuilder() {
    throw new UnsupportedOperationException();
  }

  /**
   * Creates a copy of this node with a new {@link Command} instance.
   *
   * @param command the new command to associate with the node
   * @return a new {@link VelocityArgumentCommandNode} with the updated command
   */
  public VelocityArgumentCommandNode<S, T> withCommand(final Command<S> command) {
    return new VelocityArgumentCommandNode<>(getName(), type, command, getRequirement(),
        getContextRequirement(), getRedirect(), getRedirectModifier(), isFork(), getCustomSuggestions());
  }

  /**
   * Creates a copy of this node with a new redirection target.
   *
   * @param target the new node to redirect to
   * @return a new {@link VelocityArgumentCommandNode} with the updated redirect target
   */
  public VelocityArgumentCommandNode<S, T> withRedirect(final CommandNode<S> target) {
    return new VelocityArgumentCommandNode<>(getName(), type, getCommand(), getRequirement(),
        getContextRequirement(), target, getRedirectModifier(), isFork(), getCustomSuggestions());
  }

  /**
   * Returns {@code true} for all inputs.
   * Since the argument type is greedy, all input is assumed valid.
   *
   * @param input the input to validate
   * @return always {@code true}
   */
  @Override
  public boolean isValidInput(final String input) {
    return true;
  }

  /**
   * Disallows adding child nodes to this node. Always throws.
   *
   * @param node the child node (ignored)
   * @throws UnsupportedOperationException always
   */
  @Override
  public void addChild(final CommandNode<S> node) {
    throw new UnsupportedOperationException("Cannot add children to a greedy node");
  }

  /**
   * Compares this node to another for structural equality.
   *
   * @param o the object to compare
   * @return true if both nodes are equal
   */
  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof VelocityArgumentCommandNode<?, ?> that)) {
      return false;
    }

    if (!super.equals(that)) {
      return false;
    }

    return this.type.equals(that.type);
  }

  /**
   * Returns the hash code of this node.
   *
   * @return the computed hash code
   */
  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + this.type.hashCode();
    return result;
  }

  /**
   * Returns examples of valid inputs as defined by the wrapped {@link ArgumentType}.
   *
   * @return example values for the argument
   */
  @Override
  public Collection<String> getExamples() {
    return this.type.getExamples();
  }

  /**
   * Returns a human-readable representation of this argument node.
   *
   * @return a string representation of the node
   */
  @Override
  public String toString() {
    return "<argument " + this.getName() + ":" + this.type + ">";
  }
}
