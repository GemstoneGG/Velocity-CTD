/*
 * Copyright (C) 2020-2024 Velocity Contributors
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
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import net.kyori.adventure.text.Component;

/**
 * Implements the {@code /queue} command.
 */
public class QueueCommand {

  private final VelocityServer server;

  public QueueCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(final boolean isQueueEnabled) {
    if (!isQueueEnabled) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder("queue")
        .requires(source -> source.getPermissionValue("velocity.queue") == Tristate.TRUE)
        .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
            .suggests((ctx, builder) -> {
              final String argument = ctx.getArguments().containsKey("server")
                  ? StringArgumentType.getString(ctx, "server")
                  : "";
              for (final RegisteredServer sv : server.getAllServers()) {
                final String serverName = sv.getServerInfo().getName();
                if (serverName.regionMatches(true, 0, argument, 0, argument.length())) {
                  if (ctx.getSource().getPermissionValue("velocity.command.server." + serverName) != Tristate.FALSE) {
                    builder.suggest(serverName);
                  }
                }
              }
              return builder.buildFuture();
            })
            .executes(this::queue)
        );

    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .aliases(server.getConfiguration().getQueue().getQueueAliases().toArray(new String[0]))
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int queue(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = QueueAdminCommand.getServer(this.server, ctx, "server");

    if (server == null) {
      return -1;
    }

    if (ctx.getSource() instanceof Player player) {
      if (server.getQueueStatus().isQueued(player)) {
        player.sendMessage(Component.translatable("velocity.queue.error.already-queued")
            .arguments(Component.text(server.getServerInfo().getName())));
        return -1;
      }

      if (server.getQueueStatus().queueWithIndication(player)) {
        player.sendMessage(Component.translatable("velocity.queue.command.queued")
            .arguments(Component.text(server.getServerInfo().getName())));
        return Command.SINGLE_SUCCESS;
      } else {
        player.sendMessage(Component.translatable("velocity.queue.error.paused")
            .arguments(Component.text(server.getServerInfo().getName())));
        return -1;
      }
    } else {
      ctx.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
      return -1;
    }
  }
}
