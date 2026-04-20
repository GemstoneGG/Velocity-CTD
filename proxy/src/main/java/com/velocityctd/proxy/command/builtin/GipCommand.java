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
 * Implements Velocity-CTD's {@code /gip} command.
 */
public class GipCommand implements BuiltinCommandDefinition {

  private final VelocityServer server;

  public GipCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "gip";
  }

  @Override
  public BrigadierCommand build() {
    RequiredArgumentBuilder<CommandSource, String> playerNode = BrigadierCommand
        .requiredArgumentBuilder("player", StringArgumentType.word())
        .suggests((ctx, builder) -> CommandUtils.suggestPlayer(server, ctx, builder))
        .executes(this::executeIp);

    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder(label())
        .requires(source -> source.getPermissionValue("velocity.command.gip") == Tristate.TRUE)
        .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.gip.usage"))
        .then(playerNode);

    return new BrigadierCommand(rootNode);
  }

  private int executeIp(CommandContext<CommandSource> context) {
    String playerName = context.getArgument("player", String.class);
    Optional<VelocityClusterPlayer> maybePlayer = server.getClusterPlayerService().getPlayer(playerName);

    if (maybePlayer.isEmpty()) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Argument.string("player", playerName))
      );
      return 0;
    }

    VelocityClusterPlayer player = maybePlayer.get();
    if (player.getIpAddress() == null) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Argument.string("player", playerName))
      );
      return 0;
    }

    context.getSource().sendMessage(
        Component.translatable("velocity.command.gip.message")
            .arguments(
                Argument.string("0", player.getUsername()),
                Argument.string("1", player.getIpAddress())
            )
    );

    return Command.SINGLE_SUCCESS;
  }
}
