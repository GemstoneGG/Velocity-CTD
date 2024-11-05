/*
 * Copyright (C) 2020-2024 Velocity Contributors
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

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements the Velocity default {@code /plist} command.
 */
public class PlistCommand {

  private static final String SERVER_ARG = "server";
  private static final String PROXY_ARG = "proxy";

  private final VelocityServer server;

  public PlistCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers this command.
   */
  public void register(final boolean isPlistEnabled) {
    if (!isPlistEnabled || !server.getConfiguration().getRedis().isEnabled()) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder("plist")
        .requires(source ->
            source.getPermissionValue("velocity.command.plist") == Tristate.TRUE)
        .executes(this::totalCount);
    final ArgumentCommandNode<CommandSource, String> serverNode = BrigadierCommand
        .requiredArgumentBuilder(PROXY_ARG, StringArgumentType.string())
        .suggests((context, builder) -> {
          final String argument = context.getArguments().containsKey(PROXY_ARG)
              ? context.getArgument(PROXY_ARG, String.class)
              : "";
          for (String proxyId : server.getMultiProxyHandler().getAllProxyIds()) {
            if (proxyId.regionMatches(true, 0, argument, 0, argument.length())) {
              builder.suggest(proxyId);
            }
          }
          return builder.buildFuture();
        })
        .executes(this::proxyCount)
        .then(
          BrigadierCommand.requiredArgumentBuilder(SERVER_ARG, StringArgumentType.string())
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
          .build()
        )
        .build();
    rootNode.then(serverNode);
    server.getCommandManager().register(new BrigadierCommand(rootNode));
  }

  private int totalCount(final CommandContext<CommandSource> context) {
    final CommandSource source = context.getSource();
    sendTotalProxyCount(source, this.server.getMultiProxyHandler().getOwnProxyId(), this.server.getPlayerCount());
    source.sendMessage(
        Component.translatable("velocity.command.plist-view-proxy", NamedTextColor.YELLOW));
    return 1;
  }

  private List<MultiProxyHandler.RemotePlayerInfo> getProxyPlayers(final CommandContext<CommandSource> context) {
    final String proxyName = getString(context, PROXY_ARG);
    final List<MultiProxyHandler.RemotePlayerInfo> proxyPlayers = server.getMultiProxyHandler().getPlayers(proxyName);

    if (proxyPlayers == null) {
      context.getSource().sendMessage(Component.translatable("velocity.command.proxy-does-not-exist", NamedTextColor.RED)
          .arguments(Component.text(proxyName))
      );
    }

    return proxyPlayers;
  }

  private int serverCount(final CommandContext<CommandSource> context) {
    final List<MultiProxyHandler.RemotePlayerInfo> proxyPlayers = getProxyPlayers(context);

    if (proxyPlayers == null) {
      return -1;
    }

    final String serverName = getString(context, SERVER_ARG);

    if (!Objects.equals(serverName, "all")) {
      proxyPlayers.removeIf(it -> it.serverName == null || !it.serverName.equalsIgnoreCase(serverName));
    }

    sendServerPlayers(context.getSource(), proxyPlayers, serverName);

    if (Objects.equals(serverName, "all")) {
      sendTotalProxyCount(context.getSource(), context.getArgument(PROXY_ARG, String.class), proxyPlayers.size());
    }

    return Command.SINGLE_SUCCESS;
  }

  private int proxyCount(final CommandContext<CommandSource> context) {
    final List<MultiProxyHandler.RemotePlayerInfo> proxyPlayers = getProxyPlayers(context);

    if (proxyPlayers == null) {
      return -1;
    }

    sendTotalProxyCount(context.getSource(), getString(context, PROXY_ARG), proxyPlayers.size());
    return Command.SINGLE_SUCCESS;
  }

  private void sendTotalProxyCount(final CommandSource target, final String proxyId, final int online) {
    final TranslatableComponent.Builder msg = Component.translatable()
            .key(online == 1
                  ? "velocity.command.plist-player-singular"
                  : "velocity.command.plist-player-plural"
            ).color(NamedTextColor.YELLOW)
            .arguments(
                Component.text(Integer.toString(online)),
                Component.text(proxyId)
            );
    target.sendMessage(msg.build());
  }

  private void sendServerPlayers(final CommandSource target,
                                 final List<MultiProxyHandler.RemotePlayerInfo> onServer,
                                 final String serverName) {
    onServer.stream()
        .map(it -> it.name)
        .reduce((a, b) -> a + ", " + b)
        .ifPresent(playerList -> {
          final TranslatableComponent.Builder builder = Component.translatable()
              .key("velocity.command.plist-server")
              .arguments(
                  Component.text(serverName),
                  Component.text(onServer.size()),
                  Component.text(playerList)
              );
          target.sendMessage(builder.build());
        });
  }
}
