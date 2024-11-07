/*
 * Copyright (C) 2024 Velocity Contributors
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

package com.velocitypowered.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Utilities for Velocity builtin command implementations.
 */
public class VelocityCommandUtils {
  private VelocityCommandUtils() {
    throw new UnsupportedOperationException("Cannot instantiate VelocityCommandUtils");
  }

  /**
   * Gets a player from a command argument named {@code player}.
   *
   * @param server the proxy server
   * @param ctx the command context
   * @return the found player, or {@code null} if the player couldn't be found
   */
  public static Player getPlayer(VelocityServer server, CommandContext<CommandSource> ctx) {
    String playerName = ctx.getArgument("player", String.class);
    Optional<Player> playerOptional = server.getPlayer(playerName);

    if (playerOptional.isEmpty()) {
      ctx.getSource().sendMessage(CommandMessages.PLAYER_NOT_FOUND
          .arguments(Component.text(playerName)));
      return null;
    }

    return playerOptional.get();
  }

  /**
   * Generates a suggestion provider to complete the name of a server.
   *
   * @param server            the proxy server
   * @param argName           the name of the string argument to complete
   * @param allowNonQueueable whether to suggest a server if the server has queueing disabled
   * @return a suggestion provider that completes a server name
   */
  public static SuggestionProvider<CommandSource> suggestServer(VelocityServer server, String argName,
                                                                boolean allowNonQueueable) {
    return (ctx, builder) -> {
      boolean allowNonQueueable0 = allowNonQueueable;

      final String argument = ctx.getArguments().containsKey(argName)
          ? StringArgumentType.getString(ctx, argName)
          : "";

      VelocityConfiguration.Queue queueConfig = server.getConfiguration().getQueue();

      if (!queueConfig.isEnabled()) {
        allowNonQueueable0 = true;
      }

      for (final RegisteredServer sv : server.getAllServers()) {
        final String serverName = sv.getServerInfo().getName();

        if (!allowNonQueueable0 && queueConfig.getNoQueueServers().contains(serverName)) {
          continue;
        }

        if (serverName.regionMatches(true, 0, argument, 0, argument.length())) {
          if (ctx.getSource().getPermissionValue("velocity.command.server." + serverName) != Tristate.FALSE) {
            builder.suggest(serverName);
          }
        }
      }

      return builder.buildFuture();
    };
  }

  /**
   * Fetches a server from a string in a command context.
   *
   * @param server            the proxy instance
   * @param ctx               the command context
   * @param argName           the name of the argument
   * @param allowNonQueueable whether to return a servers if it can't be queued.
   * @return the found server, or {@code null} if one couldn't be found
   */
  public static VelocityRegisteredServer getServer(VelocityServer server, CommandContext<CommandSource> ctx,
                                                   String argName, boolean allowNonQueueable) {
    String serverName = ctx.getArgument(argName, String.class);
    Optional<RegisteredServer> serverOptional = server.getServer(serverName);

    if (serverOptional.isEmpty()) {
      ctx.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(serverName)));
      return null;
    }

    VelocityRegisteredServer registeredServer = (VelocityRegisteredServer) serverOptional.get();

    if (!checkServerPermissions(registeredServer, ctx.getSource())) {
      ctx.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(serverName)));
      return null;
    }


    if (!allowNonQueueable && !registeredServer.getQueueStatus().hasQueue()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.server-has-no-queue")
          .arguments(Component.text(serverName)));
      return null;
    }

    return registeredServer;
  }

  /**
   * Checks if a command source has permission to join a server.
   *
   * @param server the server to check against
   * @param source the command source to be checked
   * @return whether the command source has permission to join
   */
  public static boolean checkServerPermissions(RegisteredServer server, CommandSource source) {
    String serverName = server.getServerInfo().getName();
    return source.getPermissionValue("velocity.command.server." + serverName) != Tristate.FALSE;
  }

  /**
   * Emits usage text for the given command name to the source of the given command context.
   *
   * @param ctx the command context to send usage to
   * @param commandName the command name
   * @return {@code Command.SINGLE_SUCCESS} to allow using in expression-style {@code .executes} lambdas.
   */
  public static int emitUsage(CommandContext<CommandSource> ctx, String commandName) {
    ctx.getSource().sendMessage(
        Component.translatable("velocity.command." + commandName + ".usage", NamedTextColor.YELLOW)
    );
    return Command.SINGLE_SUCCESS;
  }
}
