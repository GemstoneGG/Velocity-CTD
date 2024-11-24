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
import com.velocitypowered.proxy.redis.multiproxy.EncodedCommandSource;
import com.velocitypowered.proxy.redis.multiproxy.RedisGetPlayerPingRequest;
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
                .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, true))
                .executes(context -> {
                  String player = context.getArgument("player", String.class);
                  return this.getPing(context, player);
                })
        )
        .executes(context -> {
          if (context.getSource() instanceof Player player) {
            return this.getPing(context, player.getUsername());
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

  private int getPing(final CommandContext<CommandSource> context, final String username) {
    // Check if sending player matches for the response message.
    boolean matchesSender = false;
    Player player = this.server.getPlayer(username).orElse(null);

    if (context.getSource() instanceof Player sendingPlayer) {
      if (player != null && player.getUniqueId().equals(sendingPlayer.getUniqueId())) {
        matchesSender = true;
      }
    }

    if (matchesSender) {
      long ping = player.getPing();

      if (ping == -1L) {
        context.getSource().sendMessage(
                Component.translatable("velocity.command.ping.unknown", NamedTextColor.RED)
                        .arguments(Component.text(player.getUsername()))
        );
        return 0;
      }

      context.getSource().sendMessage(
          Component.translatable("velocity.command.ping.self", NamedTextColor.GREEN)
              .arguments(Component.text(ping))
      );
    } else {
      if (server.getMultiProxyHandler().isEnabled()) {
        if (!this.server.getMultiProxyHandler().isPlayerOnline(username)) {
          context.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
              .arguments(Component.text(username)));
          return -1;
        }

        this.server.getRedisManager().send(new RedisGetPlayerPingRequest(EncodedCommandSource.from(
            context.getSource(),
            this.server.getConfiguration().getRedis().getProxyId()),
            username));
      } else {
        if (player == null) {
          context.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
              .arguments(Component.text(username)));
          return -1;
        }

        Component component = Component.translatable("velocity.command.ping.other",
                NamedTextColor.GREEN)
            .arguments(Component.text(player.getUsername()),
                Component.text(player.getPing()));
        context.getSource().sendMessage(component);
      }
    }
    return Command.SINGLE_SUCCESS;
  }
}
