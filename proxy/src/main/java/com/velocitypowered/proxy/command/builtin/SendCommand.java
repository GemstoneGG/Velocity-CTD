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
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.redis.multiproxy.RedisSwitchServerRequest;
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements the Velocity default {@code /send} command.
 */
public class SendCommand {
  private final VelocityServer server;
  private static final String SERVER_ARG = "server";
  private static final String PLAYER_ARG = "player";

  public SendCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(final boolean isSendEnabled) {
    if (!isSendEnabled) {
      return;
    }

    if (server.getMultiProxyHandler().isRedisEnabled()) {
      registerMultiProxy(true);
      return;
    }

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
    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  /**
   * Handles registering the command when Redis is enabled.
   *
   * @param isSendEnabled Whether the command is enabled or not.
   */
  public void registerMultiProxy(final boolean isSendEnabled) {
    if (!isSendEnabled) {
      return;
    }

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
          for (RemotePlayerInfo info : server.getMultiProxyHandler().getAllPlayers()) {
            final String playerName = info.getName();
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
    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int usage(final CommandContext<CommandSource> context) {
    context.getSource().sendMessage(
        Component.translatable("velocity.command.send-usage", NamedTextColor.YELLOW)
    );
    return Command.SINGLE_SUCCESS;
  }

  private int send(final CommandContext<CommandSource> context) {
    if (server.getMultiProxyHandler().isRedisEnabled()) {
      return sendMultiProxy(context);
    }

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
        if (maybeServer.get().getServerInfo().getName().equalsIgnoreCase(connectedServer.get().getServerInfo().getName())) {
          context.getSource().sendMessage(Component.translatable("velocity.command.send-same-server"));
          return -1;
        }

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

      if (result.bestMatch().isEmpty()) {
        context.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST.arguments(Component.text(player.substring(1))));
        return 0;
      }

      if (result.hasMultipleMatches()) {
        context.getSource().sendMessage(CommandMessages.SERVER_MULTIPLE_MATCH);
        return 0;
      }

      final RegisteredServer sourceServer = result.bestMatch().get();
      sendPlayersFromServer(context, sourceServer, targetServer);
      return Command.SINGLE_SUCCESS;
    }

    // The player at this point must be present
    final Player player0 = maybePlayer.orElseThrow();
    sendPlayer(context, player0, targetServer);
    return Command.SINGLE_SUCCESS;
  }

  private void sendPlayer(final CommandContext<CommandSource> context, final Player player0,
                          final RegisteredServer targetServer) {
    ServerConnection cur = player0.getCurrentServer().orElse(null);
    if (cur != null && cur.getServerInfo().getName().equalsIgnoreCase(targetServer.getServerInfo().getName())) {
      context.getSource().sendMessage(Component.translatable("velocity.command.send-same-server"));
      return;
    }

    if (player0.getCurrentServer().isPresent() && player0.getCurrentServer().get().getServer().equals(targetServer)) {
      context.getSource().sendMessage(Component.translatable("velocity.command.send-player-none",
          Component.text(player0.getUsername()), Component.text(targetServer.getServerInfo().getName())));
    } else {
      player0.createConnectionRequest(targetServer).fireAndForget();
      context.getSource().sendMessage(Component.translatable("velocity.command.send-player",
          Component.text(player0.getUsername()), Component.text(targetServer.getServerInfo().getName())));
    }
  }

  private void sendPlayersFromServer(final CommandContext<CommandSource> context, final RegisteredServer server,
                                     final RegisteredServer targetServer) {

    if (server.getServerInfo().getName().equalsIgnoreCase(targetServer.getServerInfo().getName())) {
      context.getSource().sendMessage(Component.translatable("velocity.command.send-same-server"));
      return;
    }

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

  private int sendMultiProxy(final CommandContext<CommandSource> context) {
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

    if (this.server.getMultiProxyHandler().isPlayerOnline(player)
        && !Objects.equals(player, "all")
        && !Objects.equals(player, "current")
        && !player.startsWith("+")) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Component.text(player))
      );
      return 0;
    }

    if (Objects.equals(player, "all")) {
      List<RemotePlayerInfo> list = this.server.getMultiProxyHandler().getAllPlayers();
      for (final RemotePlayerInfo p : list) {
        this.server.getRedisManager().send(new RedisSwitchServerRequest(p.getName(), targetServer.getServerInfo().getName()));
      }
      final int globalCount = list.size();
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
        if (maybeServer.get().getServerInfo().getName().equalsIgnoreCase(connectedServer.get().getServerInfo().getName())) {
          context.getSource().sendMessage(Component.translatable("velocity.command.send-same-server"));
          return -1;
        }
        int amountDone = 0;
        List<RemotePlayerInfo> list = this.server.getMultiProxyHandler().getAllPlayers();
        for (final RemotePlayerInfo p : list) {
          if (p.getServerName().equalsIgnoreCase(connectedServer.get().getServerInfo().getName())) {
            this.server.getRedisManager().send(new RedisSwitchServerRequest(p.getName(), connectedServer.get().getServerInfo().getName()));
            amountDone++;
          }
        }

        context.getSource().sendMessage(Component.translatable(amountDone == 1
                ? "velocity.command.send-server-singular" : "velocity.command.send-server-plural",
            Component.text(amountDone), Component.text(connectedServer.get().getServerInfo().getName()),
            Component.text(targetServer.getServerInfo().getName())));
        return Command.SINGLE_SUCCESS;
      }
      return 0;
    }

    if (player.startsWith("+")) {

      final ServerResult result = findServer(player.substring(1));

      if (result.bestMatch().isEmpty()) {
        context.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST.arguments(Component.text(player.substring(1))));
        return 0;
      }

      if (result.hasMultipleMatches()) {
        context.getSource().sendMessage(CommandMessages.SERVER_MULTIPLE_MATCH);
        return 0;
      }

      final RegisteredServer sourceServer = result.bestMatch().get();
      sendPlayersFromServerMultiProxy(context, sourceServer, targetServer);
      return Command.SINGLE_SUCCESS;
    }

    // The player at this point must be present
    sendPlayerMultiProxy(context, player, targetServer);
    return Command.SINGLE_SUCCESS;
  }

  private void sendPlayerMultiProxy(final CommandContext<CommandSource> context, final String playerInput,
                                    final RegisteredServer targetServer) {

    RemotePlayerInfo playerInfo = server.getMultiProxyHandler().getPlayerInfo(playerInput);

    String correctName = playerInfo.getName();
    boolean alreadyConnected = playerInfo.getServerName().equalsIgnoreCase(targetServer.getServerInfo().getName());

    if (alreadyConnected) {
      context.getSource().sendMessage(Component.translatable("velocity.command.send-player-none",
              Component.text(correctName), Component.text(targetServer.getServerInfo().getName())));
    } else {
      this.server.getRedisManager().send(new RedisSwitchServerRequest(correctName,
              targetServer.getServerInfo().getName()));
      context.getSource().sendMessage(Component.translatable("velocity.command.send-player",
              Component.text(correctName), Component.text(targetServer.getServerInfo().getName())));
    }
  }

  private void sendPlayersFromServerMultiProxy(final CommandContext<CommandSource> context, final RegisteredServer server,
                                               final RegisteredServer targetServer) {
    final String name = server.getServerInfo().getName();

    if (name.equalsIgnoreCase(targetServer.getServerInfo().getName())) {
      context.getSource().sendMessage(Component.translatable("velocity.command.send-same-server"));
      return;
    }

    int amountDone = 0;
    List<RemotePlayerInfo> list = this.server.getMultiProxyHandler().getAllPlayers();
    for (final RemotePlayerInfo p : list) {
      if (p.getServerName().equalsIgnoreCase(name)) {
        this.server.getRedisManager().send(new RedisSwitchServerRequest(p.getName(), targetServer.getServerInfo().getName()));
        amountDone++;
      }
    }

    if (amountDone == 0) {
      context.getSource().sendMessage(Component.translatable("velocity.command.send-server-none",
          Component.text(name), Component.text(targetServer.getServerInfo().getName())));
      return;
    }
    for (Player targetPlayer : server.getPlayersConnected()) {
      targetPlayer.createConnectionRequest(targetServer).fireAndForget();
    }
    context.getSource().sendMessage(Component.translatable(amountDone == 1
            ? "velocity.command.send-server-singular" : "velocity.command.send-server-plural",
        Component.text(amountDone), Component.text(name),
        Component.text(targetServer.getServerInfo().getName())));
  }

  private ServerResult findServer(final String serverName) {
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

  private record ServerResult(Optional<RegisteredServer> bestMatch, boolean multipleMatches) {

    public boolean hasMultipleMatches() {
      return multipleMatches;
    }

  }
}
