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

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocityctd.proxy.command.PlayerIdentifier;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import java.util.Collection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.jspecify.annotations.NonNull;

/**
 * Implements Velocity's {@code /gkick} command.
 */
public class GkickCommand implements BuiltinCommandDefinition {

  private static final String SELECTOR_ARG = "selector";
  private static final String REASON_ARG = "reason";

  private final VelocityServer server;

  public GkickCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public @NonNull String label() {
    return "gkick";
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> command = BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(src -> src.getPermissionValue("velocity.command.gkick") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.gkick.usage"))
            .then(
                    BrigadierCommand
                            .requiredArgumentBuilder(SELECTOR_ARG, StringArgumentType.word())
                            .suggests(PlayerIdentifier.suggest(server, SELECTOR_ARG))
                            .executes(ctx -> executeKick(ctx, "<lang:velocity.command.gkick.reason>"))
                            .then(
                                    BrigadierCommand
                                            .requiredArgumentBuilder(REASON_ARG, StringArgumentType.greedyString())
                                            .executes(ctx -> executeKick(ctx, ctx.getArgument(REASON_ARG, String.class)))
                            )
            );

    return new BrigadierCommand(command);
  }

  private int executeKick(CommandContext<CommandSource> ctx, String originalReason) {
    String selector = ctx.getArgument(SELECTOR_ARG, String.class);

    Component kickReason = MiniMessage.miniMessage().deserialize(originalReason);

    PlayerIdentifier.Result result = PlayerIdentifier.resolve(server, selector, ctx.getSource());
    if (!result.success()) {
      sendResolveError(ctx.getSource(), result);
      return 0;
    }

    return switch (result.type()) {
      case PLAYER -> kickPlayers(ctx, result, kickReason);
      case SERVER, CURRENT_SERVER -> kickFromServer(ctx, result, kickReason);
      default -> kickBulk(ctx, result, kickReason);
    };
  }

  private int kickPlayers(CommandContext<CommandSource> ctx, PlayerIdentifier.Result result, Component reason) {
    Collection<VelocityClusterPlayer> players = result.players();

    if (players.size() == 1) {
      VelocityClusterPlayer player = players.iterator().next();

      player.kick(reason);

      ctx.getSource().sendMessage(
              Component.translatable("velocity.command.gkick.message")
                      .arguments(Argument.string("0", String.valueOf(player.getUsername())))
      );
      return SINGLE_SUCCESS;
    }

    return kickBulk(ctx, result, reason);
  }

  private int kickFromServer(CommandContext<CommandSource> ctx, PlayerIdentifier.Result result, Component reason) {
    String fromName = result.name();
    Collection<VelocityClusterPlayer> players = result.players();

    if (players.isEmpty()) {
      ctx.getSource().sendMessage(
              Component.translatable("velocity.command.gkick.message.server.empty")
                      .arguments(Argument.string("0", String.valueOf(fromName)))
      );
      return 0;
    }

    for (VelocityClusterPlayer player : players) {
      player.kick(reason);
    }

    int kicked = players.size();
    ctx.getSource().sendMessage(
            Component.translatable("velocity.command.gkick.message.server")
                    .arguments(Argument.string("0", String.valueOf(kicked)), Argument.string("1", String.valueOf(fromName)))
    );
    return kicked;
  }

  private int kickBulk(CommandContext<CommandSource> ctx, PlayerIdentifier.Result result, Component reason) {
    Collection<VelocityClusterPlayer> players = result.players();
    int kicked = 0;

    for (VelocityClusterPlayer player : players) {
      player.kick(reason);
      kicked++;
    }

    if (players.isEmpty()) {
      ctx.getSource().sendMessage(
              Component.translatable("velocity.command.gkick.message.proxy.empty")
      );
      return 0;
    }

    ctx.getSource().sendMessage(
            Component.translatable("velocity.command.gkick.message.proxy")
                    .arguments(Argument.string("0", String.valueOf(kicked)))
    );
    return kicked;
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
}