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
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity-CTD's {@code /gkick} command.
 */
public class GkickCommand implements BuiltinCommandDefinition {

  private static final String SELECTOR_ARG = "player";
  private static final String REASON_ARG = "reason";

  private final VelocityServer server;

  public GkickCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "gkick";
  }

  @Override
  public BrigadierCommand build() {
    RequiredArgumentBuilder<CommandSource, String> selectorNode = BrigadierCommand
        .requiredArgumentBuilder(SELECTOR_ARG, StringArgumentType.word())
        .suggests(PlayerIdentifier.suggest(server, SELECTOR_ARG))
        .executes(this::executeKick)
        .then(BrigadierCommand
            .requiredArgumentBuilder(REASON_ARG, StringArgumentType.greedyString())
            .executes(this::executeKick)
        );

    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder(label())
        .requires(source -> source.getPermissionValue("velocity.command.gkick") == Tristate.TRUE)
        .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.gkick.usage"))
        .then(selectorNode);

    return new BrigadierCommand(rootNode);
  }

  private int executeKick(CommandContext<CommandSource> context) {
    String selector = context.getArgument(SELECTOR_ARG, String.class);
    Component reason = parseReason(context);

    PlayerIdentifier.Result result = PlayerIdentifier.resolve(server, selector, context.getSource());
    if (!result.success()) {
      sendResolveError(context.getSource(), result);
      return 0;
    }

    return switch (result.type()) {
      case PLAYER -> kickPlayer(context, result, reason);
      case SERVER, CURRENT_SERVER -> kickFromServer(context, result, reason);
      case PROXY -> kickFromProxy(context, result, reason);
      default -> kickAll(context, result, reason);
    };
  }

  private int kickPlayer(CommandContext<CommandSource> context, PlayerIdentifier.Result result, Component reason) {
    VelocityClusterPlayer player = result.players().iterator().next();
    if (player.isKickBypass()) {
      context.getSource().sendMessage(Component.translatable("velocity.command.gkick.exempt")
          .arguments(Argument.string("player", player.getUsername())));
      return 0;
    }

    player.kick(reason);
    context.getSource().sendMessage(Component.translatable("velocity.command.gkick.message")
        .arguments(Argument.string("player", player.getUsername())));
    return SINGLE_SUCCESS;
  }

  private int kickFromServer(CommandContext<CommandSource> context, PlayerIdentifier.Result result, Component reason) {
    String fromName = result.name();

    Collection<VelocityClusterPlayer> players = result.players();
    if (players.isEmpty()) {
      context.getSource().sendMessage(Component.translatable("velocity.command.gkick.server-none")
          .arguments(Argument.string("server", fromName)));
      return 0;
    }

    int kicked = kickEach(players, reason);
    if (kicked == 0) {
      context.getSource().sendMessage(Component.translatable("velocity.command.gkick.server-exempt")
          .arguments(Argument.string("server", fromName)));
      return 0;
    }

    context.getSource().sendMessage(
        Component.translatable(kicked == 1 ? "velocity.command.gkick.server-singular" : "velocity.command.gkick.server-plural")
            .arguments(
                Argument.numeric("count", kicked),
                Argument.string("server", fromName)
            )
    );
    return kicked;
  }

  private int kickFromProxy(CommandContext<CommandSource> context, PlayerIdentifier.Result result, Component reason) {
    String proxyId = result.name();

    Collection<VelocityClusterPlayer> players = result.players();
    if (players.isEmpty()) {
      context.getSource().sendMessage(Component.translatable("velocity.command.gkick.proxy-none")
          .arguments(Argument.string("proxy", proxyId)));
      return 0;
    }

    int kicked = kickEach(players, reason);
    if (kicked == 0) {
      context.getSource().sendMessage(Component.translatable("velocity.command.gkick.proxy-exempt")
          .arguments(Argument.string("proxy", proxyId)));
      return 0;
    }

    context.getSource().sendMessage(
        Component.translatable(kicked == 1 ? "velocity.command.gkick.proxy-singular" : "velocity.command.gkick.proxy-plural")
            .arguments(
                Argument.numeric("count", kicked),
                Argument.string("proxy", proxyId)
            )
    );
    return kicked;
  }

  private int kickAll(CommandContext<CommandSource> context, PlayerIdentifier.Result result, Component reason) {
    Collection<VelocityClusterPlayer> players = result.players();
    if (players.isEmpty()) {
      context.getSource().sendMessage(Component.translatable("velocity.command.gkick.all-none"));
      return 0;
    }

    int kicked = kickEach(players, reason);
    if (kicked == 0) {
      context.getSource().sendMessage(Component.translatable("velocity.command.gkick.all-exempt"));
      return 0;
    }

    context.getSource().sendMessage(
        Component.translatable(kicked == 1 ? "velocity.command.gkick.all-singular" : "velocity.command.gkick.all-plural")
            .arguments(Argument.numeric("count", kicked))
    );
    return kicked;
  }

  private int kickEach(Collection<VelocityClusterPlayer> players, Component reason) {
    int kicked = 0;
    for (VelocityClusterPlayer player : players) {
      if (player.isKickBypass()) {
        continue;
      }

      player.kick(reason);
      kicked++;
    }

    return kicked;
  }

  private Component parseReason(CommandContext<CommandSource> context) {
    if (!context.getArguments().containsKey(REASON_ARG)) {
      return Component.translatable("velocity.command.gkick.reason");
    }

    return CommandUtils.deserializeComponent(context.getArgument(REASON_ARG, String.class));
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
