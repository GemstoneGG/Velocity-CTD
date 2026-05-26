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
 * Implements Velocity-CTD's {@code /alertraw} command.
 */
public class AlertRawCommand implements BuiltinCommandDefinition {

  private static final String TARGET_ARG = "target";
  private static final String MESSAGE_ARG = "message";
  private static final String NO_MESSAGE_KEY = "velocity.command.alertraw.no-message";

  private final VelocityServer server;

  public AlertRawCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "alertraw";
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(source ->
                    source.getPermissionValue("velocity.command.alertraw") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.alertraw.usage"))
            .then(BrigadierCommand
                    .requiredArgumentBuilder(TARGET_ARG, StringArgumentType.word())
                    .suggests(PlayerIdentifier.suggest(server, TARGET_ARG))
                    .executes(this::alertSingle)
                    .then(BrigadierCommand
                            .requiredArgumentBuilder(MESSAGE_ARG, StringArgumentType.greedyString())
                            .executes(this::alertWithTarget)));

    return new BrigadierCommand(rootNode);
  }

  private int alertSingle(CommandContext<CommandSource> context) {
    // Single-argument form: treat the argument as the entire message (legacy /alertraw <message>).
    String message = StringArgumentType.getString(context, TARGET_ARG);
    return AlertDispatcher.dispatch(server, context.getSource(),
        null, message, NO_MESSAGE_KEY, AlertRawCommand::format);
  }

  private int alertWithTarget(CommandContext<CommandSource> context) {
    String target = StringArgumentType.getString(context, TARGET_ARG);
    String message = StringArgumentType.getString(context, MESSAGE_ARG);
    return AlertDispatcher.dispatch(server, context.getSource(),
        target, message, NO_MESSAGE_KEY, AlertRawCommand::format);
  }

  private static Component format(String message) {
    return Component.translatable("velocity.command.alertraw.message",
        NamedTextColor.WHITE, ComponentUtils.colorify(message));
  }
}
