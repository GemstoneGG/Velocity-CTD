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
import com.velocityctd.proxy.util.CompletableUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements Velocity-CTD's {@code /ping} command.
 */
public class PingCommand implements BuiltinCommandDefinition {

  private static final Logger LOGGER = LoggerFactory.getLogger(PingCommand.class);
  private final VelocityServer server;

  public PingCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "ping";
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> node = BrigadierCommand.literalArgumentBuilder(label())
        .requires(source -> source.getPermissionValue("velocity.command.ping") == Tristate.TRUE)
        .then(
            BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                .requires(source -> source.getPermissionValue("velocity.command.ping.others") == Tristate.TRUE)
                .suggests((ctx, builder) -> CommandUtils.suggestPlayer(server, ctx, builder))
                .executes(context -> {
                  String player = context.getArgument("player", String.class);
                  return this.getPing(context, player);
                })
        )
        .executes(context -> {
          if (context.getSource() instanceof ConnectedPlayer player) {
            return this.getPing(context, player.getUsername());
          } else {
            context.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
            return 0;
          }
        });

    return new BrigadierCommand(node);
  }

  private int getPing(CommandContext<CommandSource> context, String username) {
    boolean matchesSender = false;
    ConnectedPlayer player = this.server.getPlayer(username).orElse(null);

    if (context.getSource() instanceof ConnectedPlayer sendingPlayer) {
      if (player != null && player.getUniqueId().equals(sendingPlayer.getUniqueId())) {
        matchesSender = true;
      }
    }

    if (matchesSender) {
      long ping = player.getPing();

      if (ping == -1L) {
        context.getSource().sendMessage(
            Component.translatable("velocity.command.ping.unknown", NamedTextColor.RED)
                .arguments(Argument.string("player", player.getUsername())));
        return 0;
      }

      context.getSource().sendMessage(
          Component.translatable("velocity.command.ping.self", NamedTextColor.GREEN)
              .arguments(Argument.numeric("ping", ping))
      );
    } else {
      Optional<VelocityClusterPlayer> maybeClusterPlayer = this.server.getClusterPlayerService().getPlayer(username);
      if (maybeClusterPlayer.isEmpty()) {
        context.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
            .arguments(Argument.string("player", username)));
        return 0;
      }

      CommandSource source = context.getSource();
      maybeClusterPlayer.get().queryPing().thenAccept(ping -> {
        source.sendMessage(Component.translatable("velocity.command.ping.other", NamedTextColor.GREEN)
            .arguments(Argument.string("player", username), Argument.numeric("ping", ping)));
      }).exceptionally(ex -> {
        if (CompletableUtils.cause(ex) instanceof TimeoutException) {
          source.sendMessage(Component.translatable("velocity.command.ping.timeout", NamedTextColor.RED));
        } else {
          LOGGER.error("Failed to query player ping for {}", username, ex);
        }
        return null;
      });
    }

    return SINGLE_SUCCESS;
  }
}
