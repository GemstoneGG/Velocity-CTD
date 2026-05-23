/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity-CTD's {@code /find} command.
 */
public class FindCommand implements BuiltinCommandDefinition {

  private final VelocityServer server;

  public FindCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "find";
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder(label())
        .requires(source -> source.getPermissionValue("velocity.command.find") == Tristate.TRUE)
        .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.find.usage"));
    RequiredArgumentBuilder<CommandSource, String> playerNode = BrigadierCommand
        .requiredArgumentBuilder("player", StringArgumentType.word())
        .suggests((ctx, builder) -> CommandUtils.suggestPlayer(server, ctx, builder))
        .executes(this::find);

    rootNode.then(playerNode);
    return new BrigadierCommand(rootNode);
  }

  private int find(CommandContext<CommandSource> context) {
    String player = context.getArgument("player", String.class);
    Optional<VelocityClusterPlayer> maybePlayer = server.getClusterPlayerService().getPlayer(player);
    if (maybePlayer.isEmpty()) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Argument.string("player", player))
      );
      return 0;
    }

    VelocityClusterPlayer clusterPlayer = maybePlayer.get();
    if (clusterPlayer.getServerName() == null) {
      context.getSource().sendMessage(
          CommandMessages.PLAYER_NOT_FOUND.arguments(Argument.string("player", player))
      );
      return 0;
    }

    String serverDisplay = server.getClusterProxyService().isMultiProxy()
        ? clusterPlayer.getServerName() + " (" + clusterPlayer.getProxyId() + ")"
        : clusterPlayer.getServerName();

    context.getSource().sendMessage(
        Component.translatable("velocity.command.find.message", NamedTextColor.YELLOW)
            .arguments(
                Argument.string("player", clusterPlayer.getUsername()),
                Argument.string("server", serverDisplay)));

    return SINGLE_SUCCESS;
  }
}
