/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.util.ComponentUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements Velocity-CTD's {@code /alert} command.
 */
public class AlertCommand implements BuiltinCommandDefinition {

  private final VelocityServer server;

  public AlertCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "alert";
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(source ->
                    source.getPermissionValue("velocity.command.alert") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.alert.usage"))
            .then(BrigadierCommand
                    .requiredArgumentBuilder("message", StringArgumentType.greedyString())
                    .executes(this::alert));

    return new BrigadierCommand(rootNode);
  }

  private int alert(CommandContext<CommandSource> context) {
    String message = StringArgumentType.getString(context, "message");
    if (message.isEmpty()) {
      context.getSource().sendMessage(
              Component.translatable("velocity.command.alert.no-message", NamedTextColor.YELLOW)
      );

      return 0;
    }

    TranslatableComponent alertComponent = Component.translatable("velocity.command.alert.message",
            NamedTextColor.WHITE, ComponentUtils.colorify(message));

    server.getClusterPlayerService().broadcastAlert(alertComponent);

    return SINGLE_SUCCESS;
  }
}
