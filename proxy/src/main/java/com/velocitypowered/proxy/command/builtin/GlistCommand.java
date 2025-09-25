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

package com.velocitypowered.proxy.command.builtin;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler;
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements the Velocity default {@code /glist} command.
 */
public class GlistCommand {

  /**
   * The name of the argument used to specify the server in the {@code /glist} command.
   */
  private static final String SERVER_ARG = "server";

  /**
   * The {@link VelocityServer} instance used to retrieve server and player information.
   */
  private final VelocityServer server;

  /**
   * Constructs a new {@link GlistCommand}.
   *
   * @param server the Velocity server instance
   */
  public GlistCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Returns the command instance if enabled, or {@code null} if disabled via configuration.
   *
   * @param isGlistEnabled whether the command is enabled
   * @return the command instance or {@code null} if disabled
   */
  public BrigadierCommand register(final boolean isGlistEnabled) {
    if (!isGlistEnabled) {
      return null;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder("glist")
        .requires(source ->
            source.getPermissionValue("velocity.command.glist") == Tristate.TRUE)
        .executes(this::totalCount);
    final ArgumentCommandNode<CommandSource, String> serverNode = BrigadierCommand
        .requiredArgumentBuilder(SERVER_ARG, StringArgumentType.string())
        .suggests((context, builder) -> {
          final String argument = context.getArguments().containsKey(SERVER_ARG)
              ? context.getArgument(SERVER_ARG, String.class)
              : "";

          for (RegisteredServer server : server.getAllServers()) {
            final String serverName = server.getServerInfo().getName();
            if (serverName.regionMatches(true, 0, argument, 0, argument.length())) {
              builder.suggest(serverName);
            }
          }

          if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
            builder.suggest("all");
          }

          return builder.buildFuture();
        })
        .executes(this::serverCount)
        .build();

    rootNode.then(serverNode);
    return new BrigadierCommand(rootNode);
  }

  private int totalCount(final CommandContext<CommandSource> context) {
    final CommandSource source = context.getSource();
    sendTotalProxyCount(source);
    source.sendMessage(
        Component.translatable("velocity.command.glist-view-all", NamedTextColor.YELLOW)
            .arguments(Argument.string("alias", VelocityCommands.readAlias(context.getNodes()))));

    return 1;
  }

  private int serverCount(final CommandContext<CommandSource> context) {
    final CommandSource source = context.getSource();
    final String serverName = getString(context, SERVER_ARG);
    if (serverName.equalsIgnoreCase("all")) {
      for (final RegisteredServer server : VelocityCommands.sortedServerList(server)) {
        sendServerPlayers(source, true, server);
      }
      sendTotalProxyCount(source);
    } else {
      final Optional<RegisteredServer> registeredServer = server.getServer(serverName);
      if (registeredServer.isEmpty()) {
        source.sendMessage(
            CommandMessages.SERVER_DOES_NOT_EXIST
                .arguments(Argument.string("server", serverName)));
        return -1;
      }
      sendServerPlayers(source, false, registeredServer.get());
    }

    return Command.SINGLE_SUCCESS;
  }

  private void sendTotalProxyCount(final CommandSource target) {
    final int online;

    if (server.getMultiProxyHandler().isRedisEnabled()) {
      online = server.getMultiProxyHandler().getTotalPlayerCount();
    } else {
      online = server.getPlayerCount();
    }

    final TranslatableComponent.Builder msg = Component.translatable()
        .key(online == 1
            ? "velocity.command.glist-player-singular"
            : "velocity.command.glist-player-plural"
        ).color(NamedTextColor.YELLOW)
        .arguments(Argument.component(
            "players", Component.text(Integer.toString(online), NamedTextColor.GREEN)));
    target.sendMessage(msg.build());
  }

  private void sendServerPlayers(final CommandSource target,
                                 final boolean fromAll, final RegisteredServer server) {
    int totalPlayers = 0;
    List<Component> players = new ArrayList<>();
    MultiProxyHandler multiProxyHandler = this.server.getMultiProxyHandler();

    if (multiProxyHandler.isRedisEnabled()) {
      for (String proxyId : multiProxyHandler.getAllProxyIds()) {
        for (RemotePlayerInfo player : multiProxyHandler.getPlayers(proxyId)) {
          if (player.getServerName() == null || !player.getServerName().equals(server.getServerInfo().getName())) {
            continue;
          }

          String key = "velocity.command.glist.proxy-"
              + (proxyId.equals(multiProxyHandler.getOwnProxyId()) ? "self" : "other");
          Component hover = Component.translatable(key).arguments(Argument.string("proxy", proxyId));
          players.add(Component.text(player.getName()).hoverEvent(HoverEvent.showText(hover)));
          totalPlayers += 1;
        }
      }
    } else {
      final List<Player> onServer = ImmutableList.copyOf(server.getPlayersConnected());
      totalPlayers = onServer.size();

      for (Player player : onServer) {
        Component hover = Component.translatable("velocity.command.glist.proxy-self");
        players.add(Component.text(player.getUsername()).hoverEvent(HoverEvent.showText(hover)));
      }
    }

    if (totalPlayers == 0 && fromAll) {
      return;
    }

    Component playerList = players.stream()
        .reduce((a, b) -> a.append(Component.text(", ")).append(b))
        .orElse(Component.text(""));
    target.sendMessage(Component.translatable("velocity.command.glist-server")
        .arguments(
            Argument.string("server", server.getServerInfo().getName()),
            Argument.numeric("count", totalPlayers),
            Argument.component("players", playerList)
        )
    );
  }
}
