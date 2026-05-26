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

import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommandManager;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * A command that executes other commands as aliases.
 * This allows for creating simple command aliases that execute more complex commands.
 */
public class ProxyAliasCommand implements SimpleCommand {

  private final VelocityServer server;

  private final String alias;

  private final List<String> commands;

  public ProxyAliasCommand(VelocityServer server, String alias, List<String> commands) {
    this.server = server;
    this.alias = alias;
    this.commands = commands;
  }

  @Override
  public void execute(@NonNull Invocation invocation) {
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
  public CompletableFuture<List<String>> suggestAsync(@NonNull Invocation invocation) {
    return CompletableFuture.completedFuture(List.of());
  }

  /**
   * Used as a requirement check here, not necessarily only for permission-checking.
   *
   * @param invocation The invocation used to check requirement predicates of the alias commands.
   * @return Whether all the alias commands pass the requirement predicate check.
   */
  @Override
  public boolean hasPermission(@NonNull Invocation invocation) {
    // If any command doesn't resolve to a CommandNode, default to true to show the suggestion.
    return passesCommandRequirements(invocation.source()).orElse(true);
  }

  /**
   * Checks if the {@code commandSource} passes all requirement predicates of the registered commands
   * referenced to by the {@code commands} list.
   *
   * @param commandSource The {@code CommandSource} to test to the {@code CommandNode<CommandSource>}'s requirement predicate with.
   * @return {@code Optional.empty()} when any of the commands couldn't be resolved to a {@code CommandNode}.
   *         {@code Optional.of(true)} if every command can be resolved to a {@code CommandNode} and
   *         all of its requirement predicates return true.
   *         {@code Optional.of(false)} if every command can be resolved to a {@code CommandNode} and
   *         any of its requirement predicates return false.
   */
  private Optional<Boolean> passesCommandRequirements(@NonNull CommandSource commandSource) {
    VelocityCommandManager commandManager = (VelocityCommandManager) server.getCommandManager();
    for (String command : commands) {
      String commandRoot = command.split(" ", 2)[0];
      CommandNode<CommandSource> commandNode = commandManager.getDispatcher().getRoot().getChild(commandRoot);
      if (commandNode == null) {
        return Optional.empty();
      }

      boolean passes = commandNode.getRequirement().test(commandSource);
      if (!passes) {
        return Optional.of(false);
      }
    }

    return Optional.of(true);
  }

  public String alias() {
    return alias;
  }

  public List<String> commands() {
    return commands;
  }
}
