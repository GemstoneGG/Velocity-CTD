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

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * A command that executes other commands as aliases.
 * This allows for creating simple command aliases that execute more complex commands.
 *
 * @param server   the proxy server instance
 * @param alias    the alias name for this command
 * @param commands the list of commands to execute when this alias is invoked
 */
public record ProxyAliasCommand(ProxyServer server, String alias, List<String> commands) implements SimpleCommand {

  @Override
  public void execute(final @NonNull Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();
    for (String command : commands) {
      String finalCommand = command.replace("{args}", String.join(" ", args));
      server.getCommandManager().executeAsync(source, finalCommand)
            .whenComplete((result, throwable) -> {
              if (throwable != null) {
                source.sendMessage(Component.translatable("velocity.error.aliases")
                    .arguments(
                        Argument.string("alias", alias),
                        Argument.string("command", command)));
              }
            });
    }
  }

  @Override
  public CompletableFuture<List<String>> suggestAsync(final @NonNull Invocation invocation) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public boolean hasPermission(final @NonNull Invocation invocation) {
    return true;
  }

  /**
   * Gets the alias name for this command.
   *
   * @return the alias name
   */
  @Override
  public String alias() {
    return alias;
  }

  /**
   * Gets the list of commands that this alias executes.
   *
   * @return the list of commands
   */
  @Override
  public List<String> commands() {
    return commands;
  }
}
