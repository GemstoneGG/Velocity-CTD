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

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.proxy.VelocityServer;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Implements Velocity's {@code /shutdown} command.
 */
public class ShutdownCommand implements BuiltinCommandDefinition {

  private final VelocityServer server;

  public ShutdownCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "shutdown";
  }

  @Override
  public @NonNull List<String> aliases() {
    return List.of("end", "stop");
  }

  @Override
  public BrigadierCommand build() {
    return new BrigadierCommand(LiteralArgumentBuilder.<CommandSource>literal(label())
            .requires(source -> source instanceof ConsoleCommandSource)
            .executes(context -> {
              server.shutdown(true);
              return Command.SINGLE_SUCCESS;
            })
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason",
                            StringArgumentType.greedyString())
                    .executes(context -> {
                      String reason = context.getArgument("reason", String.class);
                      server.shutdown(true, CommandUtils.deserializeComponent(reason));
                      return Command.SINGLE_SUCCESS;
                    })
            ).build());
  }
}
