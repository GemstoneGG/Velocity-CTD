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
import com.mojang.brigadier.context.CommandContext;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Implements Velocity-CTD's {@code /leavequeue} command.
 */
public class LeaveQueueCommand implements BuiltinCommandDefinition {

  private final VelocityServer server;

  public LeaveQueueCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return server.getConfiguration().getQueue().getLeaveQueueAliases()
            .stream()
            .findFirst()
            .orElse("leavequeue");
  }

  @Override
  public List<String> aliases() {
    return server.getConfiguration().getQueue().getLeaveQueueAliases()
            .stream()
            .skip(1) // label()
            .toList();
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand.literalArgumentBuilder(label())
            .requires(src -> src instanceof ConnectedPlayer && src.getPermissionValue("velocity.queue.leave") == Tristate.TRUE)
            .then(BrigadierCommand
                    .requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "server", false, true))
                    .executes(this::leaveQueue)
            )
            .executes(this::leaveAllQueues);

    return new BrigadierCommand(rootNode);
  }

  private int leaveAllQueues(CommandContext<CommandSource> ctx) {
    if (!(ctx.getSource() instanceof ConnectedPlayer player)) {
      return 0;
    }

    int amountDone = 0;
    for (VelocityRegisteredServer registeredServer : this.server.getAllServers()) {
      VelocityQueue<?> queue = this.server.getQueueManager().getQueue(registeredServer.getServerInfo().getName());
      if (!queue.contains(player)) {
        continue;
      }

      queue.dequeue(player);
      amountDone++;
    }

    if (amountDone == 0) {
      player.sendMessage(Component.translatable("velocity.queue.error.not-in-queue.all"));
      return 0;
    }

    player.sendMessage(Component.translatable("velocity.queue.command.left-queue.all"));

    return amountDone;
  }

  private int leaveQueue(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer registeredServer = CommandUtils.getServer(this.server, ctx, "server", false);
    if (registeredServer == null) {
      return 0;
    }

    if (!(ctx.getSource() instanceof ConnectedPlayer player)) {
      return 0;
    }

    VelocityQueue<?> queue = this.server.getQueueManager().getQueue(registeredServer.getServerInfo().getName());
    if (queue.contains(player)) {
      queue.dequeue(player);
      player.sendMessage(
          Component.translatable("velocity.queue.command.left-queue")
              .arguments(Component.text(registeredServer.getServerInfo().getName())));
      return SINGLE_SUCCESS;
    } else {
      player.sendMessage(
          Component.translatable("velocity.queue.error.not-in-queue")
              .arguments(Component.text(registeredServer.getServerInfo().getName())));
      return 0;
    }
  }
}
