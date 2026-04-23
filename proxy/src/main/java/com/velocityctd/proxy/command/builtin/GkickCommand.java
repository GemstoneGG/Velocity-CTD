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

package com.velocityctd.proxy.command.builtin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity-CTD's {@code /gkick} command.
 */
public class GkickCommand implements BuiltinCommandDefinition {

  private final VelocityServer server;

  public GkickCommand(VelocityServer server) {
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
        .suggests((ctx, builder) -> CommandUtils.suggestPlayer(server, ctx, builder))
        .executes(this::executeKick)
        .then(BrigadierCommand
            .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
            .executes(this::executeKick)
        );

    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder(label())
        .requires(source -> source.getPermissionValue("velocity.command.gkick") == Tristate.TRUE)
        .executes(ctx -> CommandUtils.emitUsage(ctx, label()))
        .then(playerNode);

    return new BrigadierCommand(rootNode);
  }

  private Component parseReason(CommandContext<CommandSource> context) {
    if (!context.getArguments().containsKey("reason")) {
      return Component.translatable("velocity.command.gkick.reason");
    }

    return CommandUtils.deserializeComponent(context.getArgument("reason", String.class));
  }

  private int executeKick(CommandContext<CommandSource> context) {
    String playerName = context.getArgument("player", String.class);
    Optional<VelocityClusterPlayer> maybePlayer = server.getClusterPlayerService().getPlayer(playerName);

    if (maybePlayer.isEmpty()) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Argument.string("player", playerName))
      );
      return 0;
    }

    VelocityClusterPlayer player = maybePlayer.get();
    player.kick(parseReason(context));

    context.getSource().sendMessage(
        Component.translatable("velocity.command.gkick.message")
            .arguments(Argument.string("0", player.getUsername()))
    );

    return Command.SINGLE_SUCCESS;
  }
}
