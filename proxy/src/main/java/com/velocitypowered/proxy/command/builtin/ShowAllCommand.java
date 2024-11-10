/*
 * Copyright (C) 2018-2024 Velocity Contributors
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
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.util.Optional;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements Velocity's {@code /showall} command.
 */
public class ShowAllCommand {

  private final ProxyServer server;

  public ShowAllCommand(final ProxyServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(final boolean isShowAllEnabled) {
    if (!isShowAllEnabled) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder("showall")
        .requires(source ->
            source.getPermissionValue("velocity.command.showall") == Tristate.TRUE)
        .executes(ctx -> VelocityCommands.emitUsage(ctx, "showall"));
    final RequiredArgumentBuilder<CommandSource, String> serverNode = BrigadierCommand
        .requiredArgumentBuilder("server", StringArgumentType.word())
        .suggests((context, builder) -> {
          final String argument = context.getArguments().containsKey("server")
              ? context.getArgument("server", String.class)
              : "";
          for (final RegisteredServer s : server.getAllServers()) {
            final String serverName = s.getServerInfo().getName();
            if (serverName.regionMatches(true, 0, argument, 0, argument.length())) {
              builder.suggest(serverName);
            }
          }
          return builder.buildFuture();
        })
        .executes(this::find);
    rootNode.then(serverNode);
    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int find(final CommandContext<CommandSource> context) {
    final String serverName = context.getArgument("server", String.class);
    final Optional<RegisteredServer> maybeServer = server.getServer(serverName);
    if (maybeServer.isEmpty()) {
      final Component errorMessage = CommandMessages.PLAYER_NOT_FOUND.arguments(Component.text(serverName));
      context.getSource().sendMessage(errorMessage);
      return 0;
    }

    final RegisteredServer server = maybeServer.orElse(null);
    final int connectedPlayers = server.getPlayersConnected().size();

    final Component header = Component.translatable(connectedPlayers == 0 ? "velocity.command.showall.header-none"
                : (connectedPlayers == 1 ? "velocity.command.showall.header-singular"
                : "velocity.command.showall.header-plural"), NamedTextColor.YELLOW)
        .arguments(Component.text(connectedPlayers), Component.text(server.getServerInfo().getName()));

    context.getSource().sendMessage(header);

    if (connectedPlayers > 0) {
      final Component message;
      if (connectedPlayers == 1) {
        final Player singlePlayer = server.getPlayersConnected().iterator().next();
        message = Component.translatable("velocity.command.showall.message", NamedTextColor.WHITE,
                Component.text(singlePlayer.getUsername()));
      } else {
        final String playerList = server.getPlayersConnected().stream()
                .map(Player::getUsername)
                .collect(Collectors.joining(", "));
        message = Component.translatable("velocity.command.showall.message", NamedTextColor.WHITE,
                Component.text(playerList));
      }
      context.getSource().sendMessage(message);
    }
    return Command.SINGLE_SUCCESS;
  }
}
