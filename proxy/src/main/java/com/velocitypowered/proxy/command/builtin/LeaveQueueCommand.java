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
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueLeaveRequest;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.List;
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

    final List<String> aliases = server.getConfiguration().getQueue().getLeaveQueueAliases();

    if (aliases.isEmpty()) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand.literalArgumentBuilder(aliases.remove(0))
        .requires(source -> source.getPermissionValue("velocity.queue.leave") == Tristate.TRUE)
        .then(BrigadierCommand
            .requiredArgumentBuilder("server", StringArgumentType.word())
            .suggests(VelocityCommands.suggestServer(server, "server", false))
            .executes(this::leaveQueue)
        )
        .executes(this::leaveAllQueues);

    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .aliases(aliases.toArray(new String[0]))
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int leaveAllQueues(final CommandContext<CommandSource> ctx) {
    if (ctx.getSource() instanceof Player player) {

      if (!this.server.getMultiProxyHandler().isEnabled()) {
        return leaveAllQueuesNoRedis(ctx);
      }

      MultiProxyHandler.RemotePlayerInfo info = this.server.getMultiProxyHandler().getPlayerInfo(player.getUniqueId());
      if (info != null && info.getQueuedServer() == null) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-in-queue.all"));
        return -1;
      }

      for (RegisteredServer server : this.server.getAllServers()) {
        this.server.getRedisManager().send(new RedisQueueLeaveRequest(player.getUniqueId(),
                server.getServerInfo().getName(), false));
      }

      player.sendMessage(Component.translatable("velocity.queue.command.left-queue.all"));

      return Command.SINGLE_SUCCESS;
    } else {
      ctx.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
      return -1;
    }
  }

  private int leaveAllQueuesNoRedis(final CommandContext<CommandSource> ctx) {
    if (ctx.getSource() instanceof Player p) {
      int amountDone = 0;
      for (RegisteredServer server : this.server.getAllServers()) {
        ServerQueueStatus status = this.server.getQueueManager().getQueue(server.getServerInfo().getName());
        if (!status.isQueued(p.getUniqueId())) {
          continue;
        }

        status.dequeue(p.getUniqueId(), false);
        amountDone++;
      }

      if (amountDone == 0) {
        p.sendMessage(Component.translatable("velocity.queue.error.not-in-queue.all"));
        return -1;
      }

      p.sendMessage(Component.translatable("velocity.queue.command.left-queue.all"));

    }
    return Command.SINGLE_SUCCESS;
  }

  private int leaveQueue(final CommandContext<CommandSource> ctx) {
    if (!this.server.getMultiProxyHandler().isEnabled()) {
      return leaveQueueNoRedis(ctx);
    }

    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", false);
    if (server == null) {
      return -1;
    }

    if (ctx.getSource() instanceof Player player) {
      this.server.getRedisManager().send(new RedisQueueLeaveRequest(player.getUniqueId(),
              server.getServerInfo().getName(), true));
    } else {
      ctx.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
      return -1;
    }

    return Command.SINGLE_SUCCESS;
  }

  private int leaveQueueNoRedis(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", false);
    if (server == null) {
      return -1;
    }

    if (ctx.getSource() instanceof Player player) {
      ServerQueueStatus status = this.server.getQueueManager().getQueue(server.getServerInfo().getName());
      if (status == null) {
        return -1;
      }

      if (status.isQueued(player.getUniqueId())) {
        status.dequeue(player.getUniqueId(), false);
        player.sendMessage(
            Component.translatable("velocity.queue.command.left-queue")
                .arguments(Component.text(server.getServerInfo().getName())));
      } else {
        player.sendMessage(
            Component.translatable("velocity.queue.error.not-in-queue")
                .arguments(Component.text(server.getServerInfo().getName())));
      }
    } else {
      ctx.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
      return -1;
    }

    return Command.SINGLE_SUCCESS;
  }
}
