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
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity's {@code /find} command.
 */
public class FindCommand {

  /**
   * The {@link VelocityServer} instance used to access players, servers,
   * and Redis multi-proxy functionality.
   */
  private final VelocityServer server;

  /**
   * Constructs a new {@link FindCommand} using the provided {@link VelocityServer} instance.
   *
   * @param server the {@link VelocityServer} to use for player and server resolution
   */
  public FindCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Returns the command instance if enabled, or {@code null} if disabled via configuration.
   *
   * @param isFindEnabled whether the command is enabled
   * @return the command instance or {@code null} if disabled
   */
  public BrigadierCommand register(final boolean isFindEnabled) {
    if (!isFindEnabled) {
      return null;
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
    return new BrigadierCommand(rootNode);
  }

  private int find(final CommandContext<CommandSource> context) {
    if (server.getMultiProxyHandler().isRedisEnabled()) {
      return findMultiProxy(context);
    }

    final String player = context.getArgument("player", String.class);
    final Optional<Player> maybePlayer = server.getPlayer(player);
    if (maybePlayer.isEmpty()) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Argument.string("player", player))
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
        Component.translatable("velocity.command.find.message", NamedTextColor.YELLOW)
            .arguments(
                Argument.string("player", p.getUsername()),
                Argument.string("server", server.getServerInfo().getName())));

    return Command.SINGLE_SUCCESS;
  }

  private int findMultiProxy(final CommandContext<CommandSource> context) {
    final String player = context.getArgument("player", String.class);
    if (server.getMultiProxyHandler().isPlayerOnline(player)) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Argument.string("player", player))
      );

      return 0;
    }

    RemotePlayerInfo info = server.getMultiProxyHandler().getPlayerInfo(player);

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
        Component.translatable("velocity.command.find.message", NamedTextColor.YELLOW)
            .arguments(
                Argument.string("player", info.getName()),
                Argument.string("server", server.getServerInfo().getName() + " (" + info.getProxyId() + ")")));

    return Command.SINGLE_SUCCESS;
  }
}
