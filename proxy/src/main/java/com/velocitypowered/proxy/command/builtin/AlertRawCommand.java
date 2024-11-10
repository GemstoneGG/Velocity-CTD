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
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.util.ComponentUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements Velocity's {@code /alertraw} command.
 */
public class AlertRawCommand {

  private final ProxyServer server;

  public AlertRawCommand(final ProxyServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(final boolean isAlertRawEnabled) {
    if (!isAlertRawEnabled) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder("alertraw")
        .requires(source ->
            source.getPermissionValue("velocity.command.alertraw") == Tristate.TRUE)
        .executes(ctx -> VelocityCommands.emitUsage(ctx, "alertraw"))
        .then(BrigadierCommand
            .requiredArgumentBuilder("message", StringArgumentType.greedyString())
            .executes(this::alert));
    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int alert(final CommandContext<CommandSource> context) {
    String message = StringArgumentType.getString(context, "message");
    if (message.isEmpty()) {
      context.getSource().sendMessage(
          Component.translatable("velocity.command.alertraw.no-message", NamedTextColor.YELLOW)
      );
      return 0;
    }

    server.sendMessage(Component.translatable("velocity.command.alertraw.message", NamedTextColor.WHITE,
            ComponentUtils.colorify(message)));

    return Command.SINGLE_SUCCESS;
  }
}
