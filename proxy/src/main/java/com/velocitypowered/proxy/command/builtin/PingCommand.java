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
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements Velocity's {@code /ping} command.
 */
public class PingCommand {

  private final VelocityServer server;

  public PingCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(final boolean isPingEnabled) {
    if (!isPingEnabled) {
      return;
    }

    LiteralArgumentBuilder<CommandSource> node = BrigadierCommand.literalArgumentBuilder("ping")
        .requires(source -> source.getPermissionValue("velocity.command.ping") == Tristate.TRUE)
        .then(
            BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                .requires(source -> source.getPermissionValue("velocity.command.ping.others") == Tristate.TRUE)
                .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, false))
                .executes(context -> {
                  String player = context.getArgument("player", String.class);
                  Optional<Player> maybePlayer = server.getPlayer(player);

                  if (maybePlayer.isEmpty()) {
                    context.getSource().sendMessage(
                        CommandMessages.PLAYER_NOT_FOUND.arguments(Component.text(player))
                    );
                    return 0;
                  }
                  return this.getPing(context, maybePlayer.get());
                })
        )
        .executes(context -> {
          if (context.getSource() instanceof Player player) {
            return this.getPing(context, player);
          } else {
            context.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
            return 0;
          }
        });

    final BrigadierCommand command = new BrigadierCommand(node);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int getPing(final CommandContext<CommandSource> context, final Player player) {
    long ping = player.getPing();

    if (ping == -1L) {
      context.getSource().sendMessage(
          Component.translatable("velocity.command.ping.unknown", NamedTextColor.RED)
              .arguments(Component.text(player.getUsername()))
      );
      return 0;
    }

    // Check if sending player matches for the response message.
    boolean matchesSender = false;

    if (context.getSource() instanceof Player sendingPlayer) {
      if (player.getUniqueId().equals(sendingPlayer.getUniqueId())) {
        matchesSender = true;
      }
    }

    if (matchesSender) {
      context.getSource().sendMessage(
          Component.translatable("velocity.command.ping.self", NamedTextColor.GREEN)
              .arguments(Component.text(ping))
      );
    } else {
      context.getSource().sendMessage(
          Component.translatable("velocity.command.ping.other", NamedTextColor.GREEN)
              .arguments(Component.text(player.getUsername()), Component.text(ping))
      );
    }
    return Command.SINGLE_SUCCESS;
  }
}
