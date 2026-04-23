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
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocityctd.proxy.command.PlayerIdentifier;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements Velocity's {@code /send} command.
 */
public class SendCommand implements BuiltinCommandDefinition {

  private static final String SELECTOR_ARG = "selector";
  private static final String TARGET_ARG = "target";

  private final VelocityServer server;

  public SendCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "send";
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> command = BrigadierCommand
        .literalArgumentBuilder(label())
        .requires(src -> src.getPermissionValue("velocity.command.send") == Tristate.TRUE)
        .executes(ctx -> CommandUtils.emitUsage(ctx, label()))
        .then(
            BrigadierCommand
                .requiredArgumentBuilder(SELECTOR_ARG, StringArgumentType.word())
                .suggests(PlayerIdentifier.suggest(server, SELECTOR_ARG))
                .executes(ctx -> CommandUtils.emitUsage(ctx, label()))
                .then(
                    BrigadierCommand
                        .requiredArgumentBuilder(TARGET_ARG, StringArgumentType.word())
                        .suggests(this::suggestServers)
                        .executes(this::executeSend)
                )
        );

    return new BrigadierCommand(command);
  }

  private CompletableFuture<Suggestions> suggestServers(CommandContext<CommandSource> ctx, SuggestionsBuilder builder) {
    String input = builder.getRemaining();
    for (VelocityRegisteredServer rs : server.getAllServers()) {
      String name = rs.getServerInfo().getName();
      if (startsWithIgnoreCase(name, input)) {
        builder.suggest(name);
      }
    }

    return builder.buildFuture();
  }

  private int executeSend(CommandContext<CommandSource> ctx) {
    String selector = ctx.getArgument(SELECTOR_ARG, String.class);
    String targetName = ctx.getArgument(TARGET_ARG, String.class);

    Optional<VelocityRegisteredServer> maybeTarget = server.getServer(targetName);
    if (maybeTarget.isEmpty()) {
      ctx.getSource().sendMessage(
          CommandMessages.SERVER_DOES_NOT_EXIST.arguments(Component.text(targetName))
      );
      return 0;
    }

    VelocityRegisteredServer target = maybeTarget.get();
    String toName = target.getServerInfo().getName();

    PlayerIdentifier.Result result = PlayerIdentifier.resolve(server, selector, ctx.getSource());
    if (!result.success()) {
      sendResolveError(ctx.getSource(), result);
      return 0;
    }

    return switch (result.type()) {
      case PLAYER -> sendPlayers(ctx, result, toName);
      case SERVER, CURRENT_SERVER -> sendFromServer(ctx, result, toName);
      default -> sendBulk(ctx, result, toName);
    };
  }

  private int sendPlayers(CommandContext<CommandSource> ctx, PlayerIdentifier.Result result, String toName) {
    Collection<VelocityClusterPlayer> players = result.players();

    if (players.size() == 1) {
      VelocityClusterPlayer player = players.iterator().next();
      if (equalsIgnoreCase(player.getServerName(), toName)) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.send-player-none")
            .arguments(
                Argument.string("player", player.getUsername()),
                Argument.string("server", toName)
            ));
        return Command.SINGLE_SUCCESS;
      }

      player.move(toName);
      ctx.getSource().sendMessage(Component.translatable("velocity.command.send-player")
          .arguments(
              Argument.string("player", player.getUsername()),
              Argument.string("server", toName)
          ));
      return Command.SINGLE_SUCCESS;
    }

    // Multiple comma-separated players
    return sendBulk(ctx, result, toName);
  }

  private int sendFromServer(CommandContext<CommandSource> ctx, PlayerIdentifier.Result result, String toName) {
    String fromName = result.name();

    if (equalsIgnoreCase(fromName, toName)) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.send-same-server"));
      return 0;
    }

    Collection<VelocityClusterPlayer> players = result.players();
    if (players.isEmpty()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.send-server-none")
          .arguments(
              Argument.string("server", fromName),
              Argument.string("to", toName)
          ));
      return Command.SINGLE_SUCCESS;
    }

    for (VelocityClusterPlayer player : players) {
      player.move(toName);
    }

    int moved = players.size();
    ctx.getSource().sendMessage(
        Component.translatable(moved == 1 ? "velocity.command.send-server-singular" : "velocity.command.send-server-plural")
            .arguments(
                Argument.numeric("count", moved),
                Argument.string("from", fromName),
                Argument.string("to", toName)
            )
    );
    return Command.SINGLE_SUCCESS;
  }

  private int sendBulk(CommandContext<CommandSource> ctx, PlayerIdentifier.Result result, String toName) {
    Collection<VelocityClusterPlayer> players = result.players();
    int moved = 0;
    int skippedSame = 0;

    for (VelocityClusterPlayer player : players) {
      if (equalsIgnoreCase(player.getServerName(), toName)) {
        skippedSame++;
        continue;
      }
      player.move(toName);
      moved++;
    }

    if (moved == 0 && skippedSame > 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.send-same-server"));
      return 0;
    }

    ctx.getSource().sendMessage(
        Component.translatable(moved == 1 ? "velocity.command.send-all-singular" : "velocity.command.send-all-plural")
            .arguments(
                Argument.numeric("count", moved),
                Argument.string("server", toName)
            )
    );
    return Command.SINGLE_SUCCESS;
  }

  private void sendResolveError(CommandSource source, PlayerIdentifier.Result result) {
    switch (result.type()) {
      case PLAYER -> source.sendMessage(CommandMessages.PLAYER_NOT_FOUND
          .arguments(Argument.string("player", result.name())));
      case SERVER -> source.sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(result.name())));
      case PLAYER_EXECUTOR_REQUIRED -> source.sendMessage(CommandMessages.PLAYERS_ONLY);
      default -> {
      }
    }
  }

  private static boolean equalsIgnoreCase(@Nullable String a, @Nullable String b) {
    return a != null && b != null && a.equalsIgnoreCase(b);
  }

  private static boolean startsWithIgnoreCase(String candidate, String input) {
    if (input == null || input.isEmpty()) {
      return true;
    }

    return candidate.regionMatches(true, 0, input, 0, input.length());
  }
}
