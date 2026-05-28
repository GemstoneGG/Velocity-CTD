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
import com.mojang.brigadier.context.CommandContext;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocityctd.proxy.command.PlayerIdentifier;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.util.ComponentUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements Velocity-CTD's {@code /alert} command.
 */
public class AlertCommand implements BuiltinCommandDefinition {

  private static final String INPUT_ARG = "message";
  private static final String NO_MESSAGE_KEY = "velocity.command.alert.no-message";

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
                    .requiredArgumentBuilder(INPUT_ARG, StringArgumentType.greedyString())
                    .suggests(PlayerIdentifier.suggest(server, INPUT_ARG))
                    .executes(this::alert));

    return new BrigadierCommand(rootNode);
  }

  private int alert(CommandContext<CommandSource> context) {
    String input = StringArgumentType.getString(context, INPUT_ARG);
    return AlertDispatcher.dispatch(server, context.getSource(),
        input, NO_MESSAGE_KEY, AlertCommand::format);
  }

  private static Component format(String message) {
    return Component.translatable("velocity.command.alert.message",
        NamedTextColor.WHITE, ComponentUtils.colorify(message));
  }
}
