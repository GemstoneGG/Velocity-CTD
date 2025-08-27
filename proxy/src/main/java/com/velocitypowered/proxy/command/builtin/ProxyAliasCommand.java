/*
 * Copyright (C) 2018-2025 Velocity Contributors
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A command that executes other commands as aliases.
 * This allows for creating simple command aliases that execute more complex commands.
 */
public class ProxyAliasCommand implements SimpleCommand {

  private static final Logger logger = LogManager.getLogger(ProxyAliasCommand.class);

  private final ProxyServer server;
  private final String alias;
  private final List<String> commands;

  /**
   * Creates a new proxy alias command.
   *
   * @param server the proxy server instance
   * @param alias the alias name for this command
   * @param commands the list of commands to execute when this alias is invoked
   */
  public ProxyAliasCommand(ProxyServer server, String alias, List<String> commands) {
    this.server = server;
    this.alias = alias;
    this.commands = commands;
  }

  @Override
  public void execute(@NonNull Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    // Execute each command in the list
    for (String command : commands) {
      // Replace {args} placeholder with the actual arguments
      String finalCommand = command.replace("{args}", String.join(" ", args));
      
      logger.debug("Executing proxy alias '{}': {} -> {}", alias, command, finalCommand);
      
      // Execute the command asynchronously
      server.getCommandManager().executeAsync(source, finalCommand)
          .whenComplete((result, throwable) -> {
            if (throwable != null) {
              logger.warn("Failed to execute proxy alias '{}' command: {}", alias, command, throwable);
              source.sendMessage(Component.text("Error executing alias command '" + alias + "': " + command, NamedTextColor.RED));
            }
          });
    }
  }

  @Override
  public CompletableFuture<List<String>> suggestAsync(@NonNull Invocation invocation) {
    // For now, we don't provide suggestions for alias commands
    // This could be enhanced to provide suggestions based on the target commands
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public boolean hasPermission(@NonNull Invocation invocation) {
    // By default, proxy aliases have no permission requirements
    // This can be overridden by the configuration if needed
    return true;
  }

  /**
   * Gets the alias name for this command.
   *
   * @return the alias name
   */
  public String getAlias() {
    return alias;
  }

  /**
   * Gets the list of commands that this alias executes.
   *
   * @return the list of commands
   */
  public List<String> getCommands() {
    return commands;
  }
}
