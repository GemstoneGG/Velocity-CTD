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
 * Implements the {@code /leavequeue} command.
 */
public class LeaveQueueCommand {

  private final VelocityServer server;

  public LeaveQueueCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(final boolean isQueueEnabled) {
    if (!isQueueEnabled) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand.literalArgumentBuilder("leavequeue")
        .requires(source -> source.getPermissionValue("velocity.queue.leave") == Tristate.TRUE)
        .then(BrigadierCommand
            .requiredArgumentBuilder("server", StringArgumentType.word())
            .suggests(QueueAdminCommand.suggestServer(server, "server"))
            .executes(this::leaveQueue)
        )
        .executes(this::leaveAllQueues);
    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int leaveAllQueues(final CommandContext<CommandSource> ctx) {
    if (ctx.getSource() instanceof Player player) {
      boolean leftAny = false;

      for (RegisteredServer server : this.server.getAllServers()) {
        VelocityRegisteredServer registeredServer = (VelocityRegisteredServer) server;
        if (registeredServer.getQueueStatus().dequeue(player)) {
          leftAny = true;
        }
      }

      if (leftAny) {
        player.sendMessage(Component.translatable("velocity.queue.command.left-queue.all"));
      } else {
        player.sendMessage(Component.translatable("velocity.queue.error.not-in-queue.all"));
      }

      return Command.SINGLE_SUCCESS;
    } else {
      ctx.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
      return -1;
    }
  }

  private int leaveQueue(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = QueueAdminCommand.getServer(this.server, ctx, "server");

    if (server == null) {
      return -1;
    }

    if (ctx.getSource() instanceof Player player) {
      if (server.getQueueStatus().dequeue(player)) {
        player.sendMessage(Component.translatable("velocity.queue.command.left-queue")
            .arguments(Component.text(server.getServerInfo().getName())));
        return Command.SINGLE_SUCCESS;
      } else {
        player.sendMessage(Component.translatable("velocity.queue.error.not-in-queue")
            .arguments(Component.text(server.getServerInfo().getName())));
        return -1;
      }
    } else {
      ctx.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
      return -1;
    }
  }
}
