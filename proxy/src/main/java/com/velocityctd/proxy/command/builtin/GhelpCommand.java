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

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NonNull;

/**
 * Implements Velocity-CTD's {@code /ghelp} command.
 */
public class GhelpCommand implements BuiltinCommandDefinition {

  private final VelocityServer server;

  public GhelpCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public @NonNull String label() {
    return "ghelp";
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(source -> source.getPermissionValue("velocity.command.ghelp") == Tristate.TRUE)
            .executes(this::executeHelp);

    return new BrigadierCommand(rootNode);
  }

  private int executeHelp(CommandContext<CommandSource> context) {
    CommandSource source = context.getSource();
    CommandManager commandManager = server.getCommandManager();

    List<String> accessibleCommands = new ArrayList<>();

    for (String commandLabel : commandManager.getAliases()) {
      if (commandManager.hasCommand(commandLabel, source)) {
        accessibleCommands.add("/" + commandLabel);
      }
    }

    Collections.sort(accessibleCommands);

    if (accessibleCommands.isEmpty()) {
      source.sendMessage(Component.translatable("velocity.command.ghelp.none"));
      return SINGLE_SUCCESS;
    }

    String commandsListString = String.join("\n", accessibleCommands);

    TextComponent message = Component.text()
            .append(Component.translatable("velocity.command.ghelp.title"))
            .append(Component.newline())
            .append(Component.text(commandsListString, NamedTextColor.WHITE))
            .build();

    source.sendMessage(message);

    return accessibleCommands.size();
  }
}