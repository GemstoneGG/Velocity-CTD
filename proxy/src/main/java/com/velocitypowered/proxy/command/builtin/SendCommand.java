/*
 * Copyright (C) 2020-2023 Velocity Contributors
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

package com.velocitypowered.proxy.command.builtin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements the Velocity default {@code /send} command.
 */
public class SendCommand {
  private final ProxyServer server;
  private static final String SERVER_ARG = "server";
  private static final String PLAYER_ARG = "player";

  public SendCommand(ProxyServer server) {
    this.server = server;
  }

  /**
   * Registers this command.
   */
  public void register() {
    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder("send")
        .requires(source ->
            source.getPermissionValue("velocity.command.send") == Tristate.TRUE)
        .executes(this::usage);
    final RequiredArgumentBuilder<CommandSource, String> playerNode = BrigadierCommand
        .requiredArgumentBuilder(PLAYER_ARG, StringArgumentType.word())
        .suggests((context, builder) -> {
          final String argument = context.getArguments().containsKey(PLAYER_ARG)
              ? context.getArgument(PLAYER_ARG, String.class)
              : "";
          for (final Player player : server.getAllPlayers()) {
            final String playerName = player.getUsername();
            if (playerName.regionMatches(true, 0, argument, 0, argument.length())) {
              builder.suggest(playerName);
            }
          }
          if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
            builder.suggest("all");
          }
          if ("current".regionMatches(true, 0, argument, 0, argument.length())
              && context.getSource() instanceof Player) {
            builder.suggest("current");
          }

          if (argument.isEmpty() || argument.startsWith("+")) {
            for (final RegisteredServer server : server.getAllServers()) {
              final String serverName = server.getServerInfo().getName();

              if (serverName.regionMatches(true, 0, argument, 1, argument.length() - 1)) {
                builder.suggest("+" + serverName);
              }
            }
          }

          return builder.buildFuture();
        })
        .executes(this::usage);
    final ArgumentCommandNode<CommandSource, String> serverNode = BrigadierCommand
        .requiredArgumentBuilder(SERVER_ARG, StringArgumentType.word())
        .suggests((context, builder) -> {
          final String argument = context.getArguments().containsKey(SERVER_ARG)
              ? context.getArgument(SERVER_ARG, String.class)
              : "";
          for (final RegisteredServer server : server.getAllServers()) {
            final String serverName = server.getServerInfo().getName();
            if (serverName.regionMatches(true, 0, argument, 0, argument.length())) {
              builder.suggest(server.getServerInfo().getName());
            }
          }
          return builder.buildFuture();
        })
        .executes(this::send)
        .build();
    playerNode.then(serverNode);
    rootNode.then(playerNode.build());
    server.getCommandManager().register(new BrigadierCommand(rootNode.build()));
  }

  private int usage(final CommandContext<CommandSource> context) {
    context.getSource().sendMessage(
        Component.translatable("velocity.command.send-usage", NamedTextColor.YELLOW)
    );
    return Command.SINGLE_SUCCESS;
  }

  private int send(final CommandContext<CommandSource> context) {
    final String serverName = context.getArgument(SERVER_ARG, String.class);
    final String player = context.getArgument(PLAYER_ARG, String.class);

    final Optional<RegisteredServer> maybeServer = server.getServer(serverName);

    if (maybeServer.isEmpty()) {
      context.getSource().sendMessage(
          CommandMessages.SERVER_DOES_NOT_EXIST.arguments(Component.text(serverName))
      );
      return 0;
    }

    final RegisteredServer targetServer = maybeServer.get();

    final Optional<Player> maybePlayer = server.getPlayer(player);
    if (maybePlayer.isEmpty()
        && !Objects.equals(player, "all")
        && !Objects.equals(player, "current")
        && !player.startsWith("+")) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Component.text(player))
      );
      return 0;
    }

    if (Objects.equals(player, "all")) {
      for (final Player p : server.getAllPlayers()) {
        p.createConnectionRequest(targetServer).fireAndForget();
      }
      final int globalCount = server.getAllPlayers().size();
      context.getSource().sendMessage(Component.translatable(globalCount == 1
              ? "velocity.command.send-all-singular" : "velocity.command.send-all-plural",
          Component.text(globalCount),
          Component.text(targetServer.getServerInfo().getName())));
      return Command.SINGLE_SUCCESS;
    }

    if (Objects.equals(player, "current")) {
      if (!(context.getSource() instanceof Player source)) {
        context.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
        return 0;
      }

      final Optional<ServerConnection> connectedServer = source.getCurrentServer();
      if (connectedServer.isPresent()) {
        final Collection<Player> players = connectedServer.get().getServer().getPlayersConnected();
        for (final Player p : players) {
          p.createConnectionRequest(maybeServer.get()).fireAndForget();
        }
        context.getSource().sendMessage(Component.translatable(players.size() == 1
                ? "velocity.command.send-server-singular" : "velocity.command.send-server-plural",
            Component.text(players.size()), Component.text(connectedServer.get().getServerInfo().getName()),
            Component.text(targetServer.getServerInfo().getName())));
        return Command.SINGLE_SUCCESS;
      }
      return 0;
    }

    if (player.startsWith("+")) {
      final ServerResult result = findServer(player.substring(1));

      if (result.getBestMatch().isEmpty()) {
        context.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST.arguments(Component.text(player.substring(1))));
        return 0;
      }

      if (result.hasMultipleMatches()) {
        context.getSource().sendMessage(CommandMessages.SERVER_MULTIPLE_MATCH);
        return 0;
      }

      final RegisteredServer sourceServer = result.getBestMatch().get();
      sendPlayersFromServer(context, sourceServer, targetServer);
      return Command.SINGLE_SUCCESS;
    }

    // The player at this point must be present
    final Player player0 = maybePlayer.orElseThrow();
    player0.createConnectionRequest(targetServer).fireAndForget();
    context.getSource().sendMessage(Component.translatable("velocity.command.send-player",
        Component.text(player0.getUsername()), Component.text(targetServer.getServerInfo().getName())));
    return Command.SINGLE_SUCCESS;
  }

  private void sendPlayersFromServer(CommandContext<CommandSource> context, RegisteredServer server, RegisteredServer targetServer) {
    final int playerSize = server.getPlayersConnected().size();
    final String name = server.getServerInfo().getName();

    if (playerSize == 0) {
      context.getSource().sendMessage(Component.translatable("velocity.command.send-server-none",
              Component.text(name), Component.text(targetServer.getServerInfo().getName())));
      return;
    }
    for (Player targetPlayer : server.getPlayersConnected()) {
      targetPlayer.createConnectionRequest(targetServer).fireAndForget();
    }
    context.getSource().sendMessage(Component.translatable(playerSize == 1
                    ? "velocity.command.send-server-singular" : "velocity.command.send-server-plural",
            Component.text(playerSize), Component.text(name),
            Component.text(targetServer.getServerInfo().getName())));
  }

  private ServerResult findServer(String serverName) {
    final Collection<RegisteredServer> servers = server.getAllServers();
    final String lowerServerName = serverName.toLowerCase();

    Optional<RegisteredServer> bestMatch = Optional.empty();
    boolean multipleMatches = false;

    for (RegisteredServer server : servers) {
      final String lowerName = server.getServerInfo().getName().toLowerCase();

      if (lowerName.equals(lowerServerName)) {
        bestMatch = Optional.of(server);
        break;
      }

      if (lowerName.contains(lowerServerName)) {
        if (bestMatch.isPresent()) {
          multipleMatches = true;
          break;
        }

        bestMatch = Optional.of(server);
      }
    }

    return new ServerResult(bestMatch, multipleMatches);
  }

  private class ServerResult {

    private final Optional<RegisteredServer> bestMatch;
    private final boolean multipleMatches;

    public ServerResult(Optional<RegisteredServer> bestMatch, boolean multipleMatches) {
      this.bestMatch = bestMatch;
      this.multipleMatches = multipleMatches;
    }

    public Optional<RegisteredServer> getBestMatch() {
      return bestMatch;
    }

    public boolean hasMultipleMatches() {
      return multipleMatches;
    }

  }
}
