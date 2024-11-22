/*
 * Copyright (C) 2021-2023 Velocity Contributors
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

package com.velocitypowered.proxy.command;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.InvocableCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.brigadier.VelocityArgumentCommandNode;
import com.velocitypowered.proxy.command.brigadier.VelocityBrigadierCommandWrapper;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Provides utility methods common to most {@link Command} implementations. In particular,
 * {@link InvocableCommand} implementations use the same logic for creating and parsing the alias
 * and arguments command nodes, which is contained in this class.
 */
public final class VelocityCommands {

  // Wrapping

  /**
   * Walks the command node tree and wraps all {@link Command} instances in a {@link VelocityBrigadierCommandWrapper},
   * to indicate the plugin that registered the command. This also has the side effect of cloning
   * the command node tree.
   *
   * @param delegate the command node to wrap
   * @param registrant the plugin that registered the command
   * @return the wrapped command node
   */
  public static CommandNode<CommandSource> wrap(final CommandNode<CommandSource> delegate,
      final @Nullable Object registrant) {
    Preconditions.checkNotNull(delegate, "delegate");
    if (registrant == null) {
      // the registrant is null if the `plugin` was absent when we try to register the command
      return delegate;
    }

    com.mojang.brigadier.Command<CommandSource> maybeCommand = delegate.getCommand();
    if (maybeCommand != null && !(maybeCommand instanceof VelocityBrigadierCommandWrapper)) {
      maybeCommand = VelocityBrigadierCommandWrapper.wrap(delegate.getCommand(), registrant);
    }

    if (delegate instanceof LiteralCommandNode<CommandSource> lcn) {
      var literalBuilder = shallowCopyAsBuilder(lcn, delegate.getName(), true);
      literalBuilder.executes(maybeCommand);
      // we also need to wrap any children
      for (final CommandNode<CommandSource> child : delegate.getChildren()) {
        literalBuilder.then(wrap(child, registrant));
      }
      if (delegate.getRedirect() != null) {
        literalBuilder.redirect(wrap(delegate.getRedirect(), registrant));
      }
      return literalBuilder.build();
    } else if (delegate instanceof VelocityArgumentCommandNode<CommandSource, ?> vacn) {
      return vacn.withCommand(maybeCommand)
          .withRedirect(delegate.getRedirect() != null ? wrap(delegate.getRedirect(), registrant) : null);
    } else if (delegate instanceof ArgumentCommandNode) {
      var argBuilder = delegate.createBuilder().executes(maybeCommand);
      // we also need to wrap any children
      for (final CommandNode<CommandSource> child : delegate.getChildren()) {
        argBuilder.then(wrap(child, registrant));
      }
      if (delegate.getRedirect() != null) {
        argBuilder.redirect(wrap(delegate.getRedirect(), registrant));
      }
      return argBuilder.build();
    } else {
      throw new IllegalArgumentException("Unsupported node type: " + delegate.getClass());
    }
  }

  // Normalization

  /**
   * Normalizes the given command input.
   *
   * @param input the raw command input, without the leading slash ('/')
   * @param trim  whether to remove leading and trailing whitespace from the input
   * @return the normalized command input
   */
  static String normalizeInput(final String input, final boolean trim) {
    final String command = trim ? input.trim() : input;
    int firstSep = command.indexOf(CommandDispatcher.ARGUMENT_SEPARATOR_CHAR);
    if (firstSep != -1) {
      // Aliases are case-insensitive, arguments are not
      return command.substring(0, firstSep).toLowerCase(Locale.ENGLISH)
          + command.substring(firstSep);
    } else {
      return command.toLowerCase(Locale.ENGLISH);
    }
  }

  // Parsing

  /**
   * Returns the parsed alias, used to execute the command.
   *
   * @param nodes the list of parsed nodes, as returned by {@link CommandContext#getNodes()} or
   *              {@link CommandContextBuilder#getNodes()}
   * @return the command alias
   */
  public static String readAlias(final List<? extends ParsedCommandNode<?>> nodes) {
    if (nodes.isEmpty()) {
      throw new IllegalArgumentException("Cannot read alias from empty node list");
    }
    return nodes.get(0).getNode().getName();
  }

  public static final String ARGS_NODE_NAME = "arguments";

  /**
   * Returns the parsed arguments that come after the command alias, or {@code fallback} if no
   * arguments were provided.
   *
   * @param arguments the map of parsed arguments, as returned by
   *                  {@link CommandContext#getArguments()} or
   *                  {@link CommandContextBuilder#getArguments()}
   * @param type      the type class of the arguments
   * @param fallback  the value to return if no arguments were provided
   * @param <V>       the type of the arguments
   * @return the command arguments
   */
  public static <V> V readArguments(final Map<String, ? extends ParsedArgument<?, ?>> arguments,
      final Class<V> type, final V fallback) {
    final ParsedArgument<?, ?> argument = arguments.get(ARGS_NODE_NAME);
    if (argument == null) {
      return fallback; // either no arguments were given or this isn't an InvocableCommand
    }
    final Object result = argument.getResult();
    try {
      return type.cast(result);
    } catch (final ClassCastException e) {
      throw new IllegalArgumentException("Parsed argument is of type " + result.getClass()
          + ", expected " + type, e);
    }
  }

  // Alias nodes

  /**
   * Returns whether a literal node with the given name can be added to the {@link RootCommandNode}
   * associated to a {@link CommandManager}.
   *
   * <p>This is an internal method and should not be used in user-facing
   * methods. Instead, they should lowercase the given aliases themselves.
   *
   * @param alias the alias to check
   * @return true if the alias can be registered; false otherwise
   */
  public static boolean isValidAlias(final String alias) {
    return alias.equals(alias.toLowerCase(Locale.ENGLISH));
  }

  /**
   * Creates a copy of the given literal with the specified name.
   *
   * @param original the literal node to copy
   * @param newName  the name of the returned literal node
   * @return a copy of the literal with the given name
   */
  public static LiteralCommandNode<CommandSource> shallowCopy(
      final LiteralCommandNode<CommandSource> original, final String newName) {
    return shallowCopy(original, newName, original.getCommand());
  }

  /**
   * Creates a copy of the given literal with the specified name.
   *
   * @param original   the literal node to copy
   * @param newName    the name of the returned literal node
   * @param newCommand the new command to set on the copied node
   * @return a copy of the literal with the given name
   */
  private static LiteralCommandNode<CommandSource> shallowCopy(
      final LiteralCommandNode<CommandSource> original, final String newName,
      final com.mojang.brigadier.Command<CommandSource> newCommand) {
    return shallowCopyAsBuilder(original, newName, false).executes(newCommand).build();
  }

  /**
   * Creates a copy of the given literal with the specified name.
   *
   * @param original the literal node to copy
   * @param newName  the name of the returned literal node
   * @return a copy of the literal with the given name
   */
  private static LiteralArgumentBuilder<CommandSource> shallowCopyAsBuilder(
      final LiteralCommandNode<CommandSource> original, final String newName,
      final boolean skipChildren) {
    // Brigadier resolves the redirect of a node if further input can be parsed.
    // Let <bar> be a literal node having a redirect to a <foo> literal. Then,
    // the context returned by CommandDispatcher#parseNodes when given the input
    // string "<bar> " does not contain a child context with <foo> as its root node.
    // Thus, the vanilla client asks the children of <bar> for suggestions, instead
    // of those of <foo> (https://github.com/Mojang/brigadier/issues/46).
    // Perform a shallow copy of the literal instead.
    Preconditions.checkNotNull(original, "original");
    Preconditions.checkNotNull(newName, "secondaryAlias");
    final LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder
        .<CommandSource>literal(newName)
        .requires(original.getRequirement())
        .requiresWithContext(original.getContextRequirement())
        .forward(original.getRedirect(), original.getRedirectModifier(), original.isFork())
        .executes(original.getCommand());
    if (!skipChildren) {
      for (final CommandNode<CommandSource> child : original.getChildren()) {
        builder.then(child);
      }
    }
    return builder;
  }

  // Arguments node

  /**
   * Returns the arguments node for the command represented by the given alias node, if present;
   * otherwise returns {@code null}.
   *
   * @param alias the alias node
   * @param <S>   the type of the command source
   * @return the arguments node, or null if not present
   */
  static <S> @Nullable VelocityArgumentCommandNode<S, ?> getArgumentsNode(
      final LiteralCommandNode<S> alias) {
    final CommandNode<S> node = alias.getChild(ARGS_NODE_NAME);
    if (node instanceof VelocityArgumentCommandNode) {
      return (VelocityArgumentCommandNode<S, ?>) node;
    }
    return null;
  }

  /**
   * Returns whether the given node is an argument's node.
   *
   * @param node the node to check
   * @return true if the node is an arguments node; false otherwise
   */
  public static boolean isArgumentsNode(final CommandNode<?> node) {
    return node instanceof VelocityArgumentCommandNode && node.getName().equals(ARGS_NODE_NAME);
  }

  private VelocityCommands() {
    throw new AssertionError();
  }

  /**
   * Gets a player from a command argument named {@code player}.
   *
   * @param server the proxy server
   * @param ctx the command context
   * @return the found player, or {@code null} if the player couldn't be found
   */
  public static Player getPlayer(final VelocityServer server, final CommandContext<CommandSource> ctx) {
    String playerName = ctx.getArgument("player", String.class);
    Optional<Player> playerOptional = server.getPlayer(playerName);

    if (playerOptional.isEmpty()) {
      ctx.getSource().sendMessage(CommandMessages.PLAYER_NOT_FOUND
          .arguments(Component.text(playerName)));
      return null;
    }

    return playerOptional.get();
  }

  /**
   * Generates a suggestion provider to complete the name of a server.
   *
   * @param server            the proxy server
   * @param argName           the name of the string argument to complete
   * @param allowNonQueueable whether to suggest a server if the server has queueing disabled
   * @return a suggestion provider that completes a server name
   */
  public static SuggestionProvider<CommandSource> suggestServer(final VelocityServer server, final String argName,
                                                                final boolean allowNonQueueable) {
    return (ctx, builder) -> {
      boolean allowNonQueueable0 = allowNonQueueable;

      final String argument = ctx.getArguments().containsKey(argName)
          ? StringArgumentType.getString(ctx, argName)
          : "";

      VelocityConfiguration.Queue queueConfig = server.getConfiguration().getQueue();

      if (!queueConfig.isEnabled()) {
        allowNonQueueable0 = true;
      }

      for (final RegisteredServer sv : server.getAllServers()) {
        final String serverName = sv.getServerInfo().getName();

        if (!allowNonQueueable0 && queueConfig.getNoQueueServers().contains(serverName)) {
          continue;
        }

        if (serverName.regionMatches(true, 0, argument, 0, argument.length())) {
          if (ctx.getSource().getPermissionValue("velocity.command.server." + serverName) != Tristate.FALSE) {
            builder.suggest(serverName);
          }
        }
      }

      return builder.buildFuture();
    };
  }

  /**
   * Fetches a server from a string in a command context.
   *
   * @param server            the proxy instance
   * @param ctx               the command context
   * @param argName           the name of the argument
   * @param allowNonQueueable whether to return a servers if it can't be queued.
   * @return the found server, or {@code null} if one couldn't be found
   */
  public static VelocityRegisteredServer getServer(final VelocityServer server, final CommandContext<CommandSource> ctx,
                                                   final String argName, final boolean allowNonQueueable) {
    String serverName = ctx.getArgument(argName, String.class);
    Optional<RegisteredServer> serverOptional = server.getServer(serverName);

    if (serverOptional.isEmpty()) {
      ctx.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(serverName)));
      return null;
    }

    VelocityRegisteredServer registeredServer = (VelocityRegisteredServer) serverOptional.get();

    if (!checkServerPermissions(registeredServer, ctx.getSource())) {
      ctx.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(serverName)));
      return null;
    }

    if (!allowNonQueueable && !registeredServer.getQueueStatus().hasQueue()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.server-has-no-queue")
          .arguments(Component.text(serverName)));
      return null;
    }

    return registeredServer;
  }

  /**
   * Checks if a command source has permission to join a server.
   *
   * @param server the server to check against
   * @param source the command source to be checked
   * @return whether the command source has permission to join
   */
  public static boolean checkServerPermissions(final RegisteredServer server, final CommandSource source) {
    String serverName = server.getServerInfo().getName();
    return source.getPermissionValue("velocity.command.server." + serverName) != Tristate.FALSE;
  }

  /**
   * Emits usage text for the given command name to the source of the given command context.
   *
   * @param ctx the command context to send usage to
   * @param commandName the command name
   * @return {@code Command.SINGLE_SUCCESS} to allow using in expression-style {@code .executes} lambdas.
   */
  public static int emitUsage(final CommandContext<CommandSource> ctx, final String commandName) {
    String usedName = commandName;
    ParsedCommandNode<?> node = ctx.getNodes().get(0);

    if (node != null) {
      usedName = node.getNode().getName();
    }

    ctx.getSource().sendMessage(
        Component.translatable("velocity.command." + commandName + ".usage", NamedTextColor.YELLOW)
            .arguments(Component.text(usedName))
    );
    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
  }

  /**
   * Suggests the name of a connected proxy.
   *
   * @param server the proxy server instance
   * @param context the context passed to the {@code suggests} callback
   * @param builder the builder passed to the {@code builder} callback
   * @return a future that resolves to the suggestions
   */
  public static CompletableFuture<Suggestions> suggestProxy(
      final VelocityServer server, final CommandContext<CommandSource> context, final SuggestionsBuilder builder
  ) {
    final String argument = context.getArguments().containsKey("proxy")
        ? context.getArgument("proxy", String.class)
        : "";
    for (String proxyId : server.getMultiProxyHandler().getAllProxyIds()) {
      if (proxyId.regionMatches(true, 0, argument, 0, argument.length())) {
        builder.suggest(proxyId);
      }
    }
    return builder.buildFuture();
  }

  /**
   * Suggests the name of an online player.
   *
   * @param server the proxy server instance
   * @param ctx the context passed to the {@code suggests} callback
   * @param builder the builder passed to the {@code builder} callback
   * @return a future that resolves to the suggestions
   */
  public static CompletableFuture<Suggestions> suggestPlayer(
      final VelocityServer server, final CommandContext<CommandSource> ctx, final SuggestionsBuilder builder,
      final boolean includeRemote) {
    final String argument = ctx.getArguments().containsKey("player")
        ? ctx.getArgument("player", String.class)
        : "";

    if (includeRemote && server.getMultiProxyHandler().isEnabled()) {
      for (MultiProxyHandler.RemotePlayerInfo info : server.getMultiProxyHandler().getAllPlayers()) {
        if (info.getName().regionMatches(true, 0, argument, 0, argument.length())) {
          builder.suggest(info.getName());
        }
      }

      return builder.buildFuture();
    }

    for (final Player player : server.getAllPlayers()) {
      final String playerName = player.getUsername();
      if (playerName.regionMatches(true, 0, argument, 0, argument.length())) {
        builder.suggest(playerName);
      }
    }

    return builder.buildFuture();
  }

  /**
   * Returns the server list sorted by name.
   *
   * @param proxy the proxy server instance
   * @return a list of all registered servers, sorted by name
   */
  public static List<RegisteredServer> sortedServerList(final ProxyServer proxy) {
    List<RegisteredServer> servers = new ArrayList<>(proxy.getAllServers());
    servers.sort(Comparator.comparing(RegisteredServer::getServerInfo));
    return Collections.unmodifiableList(servers);
  }
}
