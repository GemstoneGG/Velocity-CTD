/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.command;

import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocityctd.api.queue.QueueState;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.checkerframework.checker.nullness.qual.NonNull;

public class CommandUtils {

  private CommandUtils() {
    throw new AssertionError();
  }

  /**
   * Generates a suggestion provider to complete the name of a server.
   *
   * @param server the proxy server
   * @param argName the name of the string argument to complete
   * @param allowNonQueueable whether to suggest a server if the server has queueing disabled
   * @param performPermissionCheck whether to perform permission checks before including a server as a suggestion.
   *                               {@code magicServers}, if any, will also be included in this permission check.
   *                               {@code "velocity.command.server.<name>"} will be used as the permission.
   * @param magicServers "magic servers" to add, if any. useful for including an "all" argument option.
   * @return a suggestion provider that completes a server name
   */
  public static SuggestionProvider<CommandSource> suggestServer(VelocityServer server, String argName,
                                                                boolean allowNonQueueable, boolean performPermissionCheck,
                                                                String... magicServers) {
    return (ctx, builder) -> {
      String argument = ctx.getArguments().containsKey(argName)
          ? StringArgumentType.getString(ctx, argName)
          : "";

      VelocityConfiguration.Queue queueConfig = server.getConfiguration().getQueue();

      List<String> possibilities = server.getAllServers().stream()
          .map(s -> s.getServerInfo().getName())
          .filter(s -> allowNonQueueable || !queueConfig.isEnabled() || !queueConfig.getNoQueueServers().contains(s))
          .collect(Collectors.toList());

      possibilities.addAll(Arrays.asList(magicServers));

      for (String possibility : possibilities) {
        if (possibility.regionMatches(true, 0, argument, 0, argument.length())) {
          if (performPermissionCheck && ctx.getSource().getPermissionValue("velocity.command.server." + possibility) == Tristate.FALSE) {
            continue;
          }

          builder.suggest(possibility);
        }
      }

      return builder.buildFuture();
    };
  }

  /**
   * Fetches a server from a string in a command context.
   *
   * @param server the proxy instance
   * @param ctx the command context
   * @param argName the name of the argument
   * @param allowNonQueueable whether to return a server if it can't be queued.
   * @return the found server, or {@code null} if one couldn't be found
   */
  public static VelocityRegisteredServer getServer(VelocityServer server, CommandContext<CommandSource> ctx,
                                                   String argName, boolean allowNonQueueable) {
    String serverName = ctx.getArgument(argName, String.class);
    Optional<VelocityRegisteredServer> serverOptional = server.getServer(serverName);

    if (serverOptional.isEmpty()) {
      ctx.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(serverName)));
      return null;
    }

    VelocityRegisteredServer registeredServer = serverOptional.get();

    if (!checkServerPermissions(registeredServer, ctx.getSource())) {
      ctx.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(serverName)));
      return null;
    }

    if (!allowNonQueueable && registeredServer.getQueue().getState() == QueueState.INACTIVE) {
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
  public static boolean checkServerPermissions(VelocityRegisteredServer server, CommandSource source) {
    String serverName = server.getServerInfo().getName();
    return source.getPermissionValue("velocity.command.server." + serverName) != Tristate.FALSE;
  }

  /**
   * Emits usage text for the given command name to the source of the given command context.
   *
   * @param ctx the command context to send usage to
   * @param key the usage message translatable key
   * @return {@code 0} to allow using in expression-style {@code .executes} lambdas, and to indicate a failed state
   */
  public static int emitUsage(CommandContext<CommandSource> ctx, String key) {
    ParsedCommandNode<?> node = ctx.getNodes().getFirst();
    String usedName = node.getNode().getName();

    ctx.getSource().sendMessage(
        Component.translatable(key, NamedTextColor.YELLOW)
            .arguments(Argument.string("command", usedName)));
    return 0;
  }

  /**
   * Generates a suggestion provider to complete the name of a proxy.
   *
   * @param server the proxy server
   * @param magicProxies "magic proxies" to add, if any. useful for including an "all" argument option.
   * @return a future that resolves to the suggestions
   */
  public static SuggestionProvider<CommandSource> suggestProxy(VelocityServer server, String argName,
                                                               String... magicProxies) {
    return (ctx, builder) -> {
      String argument = ctx.getArguments().containsKey(argName)
          ? ctx.getArgument(argName, String.class)
          : "";

      List<String> possibilities = new ArrayList<>(server.getClusterProxyService().getAllProxyIds());
      possibilities.addAll(Arrays.asList(magicProxies));

      for (String possibility : possibilities) {
        if (possibility.toLowerCase().regionMatches(true, 0, argument.toLowerCase(), 0, argument.length())) {
          builder.suggest(possibility);
        }
      }

      return builder.buildFuture();
    };
  }

  /**
   * Suggests the name of online cluster player(s).
   * Will suggest {@link com.velocityctd.api.cluster.ClusterPlayer} names through
   * {@link com.velocityctd.proxy.cluster.VelocityClusterPlayerService#getPlayerNames()},
   * so the player names suggested by this method may be online on other proxies instead.
   *
   * @param server the proxy server instance
   * @param ctx the context passed to the {@code suggests} callback
   * @param builder the builder passed to the {@code builder} callback
   * @return a future that resolves to the suggestions
   */
  public static CompletableFuture<Suggestions> suggestPlayer(VelocityServer server, CommandContext<CommandSource> ctx,
                                                             SuggestionsBuilder builder) {
    String argument = ctx.getArguments().containsKey("player")
        ? ctx.getArgument("player", String.class)
        : "";
    Collection<String> playerNames = server.getClusterPlayerService().getPlayerNames();

    for (String playerName : playerNames) {
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
  public static List<VelocityRegisteredServer> sortedServerList(VelocityServer proxy) {
    List<VelocityRegisteredServer> servers = new ArrayList<>(proxy.getAllServers());
    servers.sort(Comparator.comparing(VelocityRegisteredServer::getServerInfo));
    return Collections.unmodifiableList(servers);
  }

  /**
   * Sends or enqueues a player to a target server. Will throw if the player is already on the target server,
   * the caller must check this first.
   * Assumes `player` is connected to this proxy instance.
   *
   * @param proxyServer The VelocityServer instance
   * @param player The player to send or enqueue to `target`
   * @param target The target server to send or enqueue `player` to
   */
  public static void sendOrQueue(VelocityServer proxyServer, ConnectedPlayer player, VelocityRegisteredServer target) {
    Objects.requireNonNull(proxyServer, "proxyServer");
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(target, "target");

    VelocityServerConnection connection = player.getCurrentServer().orElse(null);
    if (connection != null && connection.getServerInfo().getName().equalsIgnoreCase(target.getServerInfo().getName())) {
      throw new IllegalArgumentException("Player is already on target server.");
    }

    if (proxyServer.getConfiguration().getQueue().getNoQueueServers().contains(target.getServerInfo().getName())
        || !proxyServer.isQueueEnabled()
        || player.hasPermission("velocity.queue.bypass")) {
      player.createConnectionRequest(target).connectWithIndication();
    } else {
      proxyServer.getQueueManager().queue(player, target);
    }
  }

  /**
   * Deserializes a raw string into a {@link Component}, trying JSON first and falling back to
   * MiniMessage. Strings that start with <code>{</code>, <code>[</code>, or <code>"</code>
   * are attempted as JSON; everything else goes straight to MiniMessage.
   *
   * @param raw the raw string to deserialize
   * @return the deserialized component
   */
  public static Component deserializeComponent(@NonNull String raw) {
    if (raw.startsWith("{") || raw.startsWith("[") || raw.startsWith("\"")) {
      try {
        return GsonComponentSerializer.gson().deserializeOrNull(raw);
      } catch (JsonSyntaxException ignored) {
        // fall through to MiniMessage
      }
    }

    return MiniMessage.miniMessage().deserialize(raw);
  }
}
