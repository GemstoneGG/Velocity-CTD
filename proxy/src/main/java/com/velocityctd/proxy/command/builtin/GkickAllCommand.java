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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;

import java.util.Collection;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

/**
 * Implements Velocity-CTD's {@code /gkickall} command.
 */
public class GkickAllCommand implements BuiltinCommandDefinition {

  private final VelocityServer server;

  public GkickAllCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "gkickall";
  }

  @Override
  public BrigadierCommand build() {
    RequiredArgumentBuilder<CommandSource, String> reasonNode = BrigadierCommand
            .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
            .executes(this::executeKickAll);

    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(source -> source.getPermissionValue("velocity.command.gkickall") == Tristate.TRUE)
            .executes(this::executeKickAll)
            .then(reasonNode);

    return new BrigadierCommand(rootNode);
  }

  private Component parseReason(CommandContext<CommandSource> context) {
    if (!context.getArguments().containsKey("reason")) {
      return Component.translatable("velocity.command.gkickall.reason");
    }

    return CommandUtils.deserializeComponent(context.getArgument("reason", String.class));
  }

  private int executeKickAll(CommandContext<CommandSource> context) {
    Collection<VelocityClusterPlayer> players = server.getClusterPlayerService().getAllPlayers();

    if (players.isEmpty()) {
      context.getSource().sendMessage(Component.translatable("velocity.command.gkickall.none"));
      return 0;
    }

    Component reason = parseReason(context);
    int kickedPlayers = 0;

    for (VelocityClusterPlayer player : players) {
      if (server.getPlayer(player.getUniqueId()).isPresent() && server.getPlayer(player.getUniqueId()).get().getPermissionValue("velocity.command.kickall.bypass") == Tristate.TRUE) {
        continue;
      }

      player.kick(reason);
      kickedPlayers++;
    }

    context.getSource().sendMessage(
            Component.translatable("velocity.command.gkickall.message")
                    .arguments(Argument.string("0", String.valueOf(kickedPlayers)))
    );

    return SINGLE_SUCCESS;
  }
}
