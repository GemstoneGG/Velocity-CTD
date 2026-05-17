/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.adventure.ClickCallbackManager;
import java.util.UUID;

public class CallbackCommand implements BuiltinCommandDefinition {

  public CallbackCommand(VelocityServer server) {
  }

  @Override
  public String label() {
    return ClickCallbackManager.COMMAND_LABEL;
  }

  @Override
  public BrigadierCommand build() {
    LiteralCommandNode<CommandSource> node = BrigadierCommand
            .literalArgumentBuilder(label())
            .then(BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.word())
                    .executes(this::execute))
            .build();

    return new BrigadierCommand(node);
  }

  private int execute(CommandContext<CommandSource> context) {
    String providedId = StringArgumentType.getString(context, "id");
    UUID id;
    try {
      id = UUID.fromString(providedId);
    } catch (IllegalArgumentException ignored) {
      return 0;
    }

    ClickCallbackManager.INSTANCE.runCallback(context.getSource(), id);
    return SINGLE_SUCCESS;
  }
}
