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
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements Velocity's {@code /find} command.
 */
public class FindCommand {

  private final VelocityServer server;

  public FindCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(final boolean isFindEnabled) {
    if (!isFindEnabled) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder("find")
        .requires(source ->
          source.getPermissionValue("velocity.command.find") == Tristate.TRUE)
        .executes(ctx -> VelocityCommands.emitUsage(ctx, "find"));
    final RequiredArgumentBuilder<CommandSource, String> playerNode = BrigadierCommand
        .requiredArgumentBuilder("player", StringArgumentType.word())
        .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, true))
        .executes(this::find);
    rootNode.then(playerNode);
    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int find(final CommandContext<CommandSource> context) {
    if (server.getMultiProxyHandler().isEnabled()) {
      return findMultiProxy(context);
    }

    final String player = context.getArgument("player", String.class);
    final Optional<Player> maybePlayer = server.getPlayer(player);
    if (maybePlayer.isEmpty()) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Component.text(player))
      );
      return 0;
    }

    // Can't be null, already checking if it's empty before
    Player p = maybePlayer.get();
    ServerConnection connection = p.getCurrentServer().orElse(null);
    if (connection == null) {
      context.getSource().sendMessage(
          Component.translatable("velocity.command.find.no-server", NamedTextColor.YELLOW)
      );
      return 0;
    }

    RegisteredServer server = connection.getServer();
    if (server == null) {
      context.getSource().sendMessage(
          Component.translatable("velocity.command.find.no-server", NamedTextColor.YELLOW)
      );
      return 0;
    }

    context.getSource().sendMessage(
        Component.translatable("velocity.command.find.message", NamedTextColor.YELLOW,
            Component.text(p.getUsername()), Component.text(server.getServerInfo().getName()))
    );
    return Command.SINGLE_SUCCESS;
  }

  private int findMultiProxy(final CommandContext<CommandSource> context) {
    final String player = context.getArgument("player", String.class);
    if (!server.getMultiProxyHandler().isPlayerOnline(player)) {
      context.getSource().sendMessage(
              CommandMessages.PLAYER_NOT_FOUND.arguments(Component.text(player))
      );
      return 0;
    }

    MultiProxyHandler.RemotePlayerInfo info = server.getMultiProxyHandler().getPlayerInfo(player);

    if (info.getServerName() == null) {
      context.getSource().sendMessage(
              Component.translatable("velocity.command.find.no-server", NamedTextColor.YELLOW)
      );
      return 0;
    }

    RegisteredServer server = this.server.getServer(info.getServerName()).orElse(null);
    if (server == null) {
      context.getSource().sendMessage(
              Component.translatable("velocity.command.find.no-server", NamedTextColor.YELLOW)
      );
      return 0;
    }

    context.getSource().sendMessage(
            Component.translatable("velocity.command.find.message", NamedTextColor.YELLOW,
                    Component.text(info.getName()), Component.text(server.getServerInfo().getName()
                            + " (" + info.getProxyId() + ")"))
    );
    return Command.SINGLE_SUCCESS;
  }
}
