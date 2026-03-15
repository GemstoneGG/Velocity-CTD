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

package com.velocityctd.proxy.commands.builtin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.impl.depot.PlayerEntry;
import com.velocityctd.proxy.redis.impl.packet.VelocityKick;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.command.builtin.BuiltinCommand;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity-CTD's {@code /gkick} command.
 */
public class GkickCommand implements BuiltinCommand {

  private final VelocityServer server;

  public GkickCommand(final VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "gkick";
  }

  @Override
  public BrigadierCommand build() {
    RequiredArgumentBuilder<CommandSource, String> playerNode = BrigadierCommand
        .requiredArgumentBuilder("player", StringArgumentType.word())
        .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, true))
        .executes(this::executeKick)
        .then(BrigadierCommand
            .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
            .executes(this::executeKick)
        );

    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder(label())
        .requires(source -> source.getPermissionValue("velocity.command.gkick") == Tristate.TRUE)
        .executes(ctx -> VelocityCommands.emitUsage(ctx, label()))
        .then(playerNode);

    return new BrigadierCommand(rootNode);
  }

  private Component parseReason(final CommandContext<CommandSource> context) {
    if (!context.getArguments().containsKey("reason")) {
      return Component.translatable("velocity.command.gkick.reason");
    }

    return VelocityCommands.deserializeComponent(context.getArgument("reason", String.class));
  }

  private int executeKick(final CommandContext<CommandSource> context) {
    if (server.isRedisEnabled()) {
      return executeKickRedis(context);
    }

    return executeKickLocal(context);
  }

  private int executeKickLocal(final CommandContext<CommandSource> context) {
    final String playerName = context.getArgument("player", String.class);
    final ConnectedPlayer player = server.getPlayer(playerName).orElse(null);

    if (player == null) {
      context.getSource().sendMessage(CommandMessages.PLAYER_NOT_FOUND);
      return 0;
    }

    player.disconnect0(parseReason(context), true);

    context.getSource().sendMessage(
        Component.translatable("velocity.command.gkick.message")
            .arguments(Argument.string("0", player.getUsername()))
    );

    return Command.SINGLE_SUCCESS;
  }

  private int executeKickRedis(final CommandContext<CommandSource> context) {
    final VelocityRedis redis = server.getRedis();
    final String playerName = context.getArgument("player", String.class);

    if (!redis.getPlayerService().isPlayerOnline(playerName)) {
      context.getSource().sendMessage(
          Component.translatable("velocity.command.gkick.no-server")
      );
      return 0;
    }

    final PlayerEntry entry = redis.getPlayerService().getPlayerEntry(playerName);
    if (entry == null) {
      context.getSource().sendMessage(
          Component.translatable("velocity.command.gkick.no-server")
      );
      return 0;
    }

    new VelocityKick(entry.getUniqueId(), parseReason(context)).publish();

    context.getSource().sendMessage(
        Component.translatable("velocity.command.gkick.message")
            .arguments(Argument.string("0", entry.getUsername()))
    );

    return Command.SINGLE_SUCCESS;
  }
}
