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

package com.velocityctd.proxy.command;

import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.proxy.util.ComponentUtils;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import com.velocitypowered.proxy.util.TranslatableMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class CommandUtils {

  private static final Logger LOGGER = LogManager.getLogger(CommandUtils.class);
  private static final PlainTextComponentSerializer PLAIN_TEXT =
      PlainTextComponentSerializer.builder().flattener(TranslatableMapper.FLATTENER).build();

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
    return suggestServer(server, argName, allowNonQueueable, performPermissionCheck, true, magicServers);
  }

  /**
   * Generates a suggestion provider to complete the name of a server, optionally excluding servers
   * marked {@code hidden-from-server-list}.
   *
   * @param server the proxy server
   * @param argName the name of the string argument to complete
   * @param allowNonQueueable whether to suggest a server if the server has queueing disabled
   * @param performPermissionCheck whether to perform permission checks before including a server as a suggestion
   * @param includeHidden whether to include servers marked {@code hidden-from-server-list}. Admin
   *                      commands should pass {@code true}; player-facing listings {@code false}.
   * @param magicServers "magic servers" to add, if any. useful for including an "all" argument option.
   * @return a suggestion provider that completes a server name
   */
  public static SuggestionProvider<CommandSource> suggestServer(VelocityServer server, String argName,
                                                                boolean allowNonQueueable, boolean performPermissionCheck,
                                                                boolean includeHidden, String... magicServers) {
    return (ctx, builder) -> {
      String argument = ctx.getArguments().containsKey(argName)
          ? StringArgumentType.getString(ctx, argName)
          : "";

      VelocityConfiguration.Queue queueConfig = server.getConfiguration().getQueue();

      for (VelocityRegisteredServer candidate : server.getAllServers()) {
        String realName = candidate.getServerInfo().getName();

        if (!includeHidden && candidate.getServerInfo().isHiddenFromServerList()) {
          continue;
        }

        if (!allowNonQueueable && queueConfig.isEnabled()
            && queueConfig.getNoQueueServers().contains(realName)) {
          continue;
        }

        // Permissions are always keyed on the real server name, never the custom id.
        if (performPermissionCheck
            && ctx.getSource().getPermissionValue("velocity.command.server." + realName) == Tristate.FALSE) {
          continue;
        }

        // Surface the custom id to players when configured, so the internal id stays hidden.
        String token = publicServerId(candidate.getServerInfo());
        if (token.regionMatches(true, 0, argument, 0, argument.length())) {
          builder.suggest(token);
        }
      }

      for (String magic : magicServers) {
        if (!magic.regionMatches(true, 0, argument, 0, argument.length())) {
          continue;
        }

        if (performPermissionCheck
            && ctx.getSource().getPermissionValue("velocity.command.server." + magic) == Tristate.FALSE) {
          continue;
        }

        builder.suggest(magic);
      }

      return builder.buildFuture();
    };
  }

  /**
   * Returns the identifier that should be shown to players for the given server: its configured
   * {@code custom-id} when present, otherwise its real name. The proxy resolves either form back to
   * the same server, so this only affects what players see and type.
   *
   * @param info the server info
   * @return the custom id if configured and non-blank, otherwise the real server name
   */
  public static String publicServerId(ServerInfo info) {
    String customId = info.getCustomId();
    return customId != null && !customId.isBlank() ? customId : info.getName();
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

    if (shouldQueue(proxyServer, player, target)) {
      proxyServer.getQueueManager().queue(player, target);
    } else {
      player.createConnectionRequest(target).connectWithIndication();
    }
  }

  /**
   * Determines whether a player being sent to `target` should be enqueued for it instead of
   * connected to it directly.
   */
  public static boolean shouldQueue(VelocityServer proxyServer, ConnectedPlayer player, VelocityRegisteredServer target) {
    return proxyServer.isQueueEnabled()
        && !proxyServer.getConfiguration().getQueue().getNoQueueServers().contains(target.getServerInfo().getName())
        && !player.hasPermission("velocity.queue.bypass");
  }

  /**
   * Handles a failed attempt to send a player to a server without telling the player about it: logs
   * the failure and, when the backend turned them away with a configured banned reason, drops them
   * from every queue. For callers that walk a chain of servers themselves and only want to report
   * its final outcome; {@code connectWithIndication} does this and more on its own.
   *
   * @param reason    the reason the backend gave, or {@code null} if it gave none
   * @param throwable the failure that aborted the attempt, or {@code null} if it completed normally
   */
  public static void reportConnectionFailure(VelocityServer proxyServer, ConnectedPlayer player,
                                             VelocityRegisteredServer target, @Nullable Component reason,
                                             @Nullable Throwable throwable) {
    String targetName = target.getServerInfo().getName();
    if (throwable != null) {
      LOGGER.error("{}: unable to connect to server {}", player, targetName, throwable);
    } else if (proxyServer.getConfiguration().isLogPlayerConnections()) {
      LOGGER.error("{}: disconnected while connecting to {}: {}", player, targetName,
          reason == null ? "" : PLAIN_TEXT.serialize(reason));
    }

    if (reason == null || !proxyServer.isQueueEnabled()) {
      return;
    }

    for (String bannedReason : proxyServer.getConfiguration().getQueue().getBannedReason()) {
      if (ComponentUtils.containsString(reason, bannedReason)) {
        proxyServer.getQueueManager().removePlayerEntirely(player);
        break;
      }
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

    return ComponentUtils.parse(raw);
  }
}
