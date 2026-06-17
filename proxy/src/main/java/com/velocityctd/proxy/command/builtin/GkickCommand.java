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
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.jspecify.annotations.NonNull;

/**
 * Implements Velocity-CTD's flexible {@code /gkick} command.
 */
public class GkickCommand implements BuiltinCommandDefinition {

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
    RequiredArgumentBuilder<CommandSource, String> reasonNode =
            BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString());

    LiteralArgumentBuilder<CommandSource> allNode = BrigadierCommand.literalArgumentBuilder("all")
            .requires(src -> src.getPermissionValue("velocity.command.gkick.all") == Tristate.TRUE)
            .executes(this::executeKickAll)
            .then(reasonNode.executes(this::executeKickAll));

    RequiredArgumentBuilder<CommandSource, String> serverNameNode =
            BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                      for (com.velocitypowered.api.proxy.server.RegisteredServer registeredServer : server.getAllServers()) {
                        builder.suggest(registeredServer.getServerInfo().getName());
                      }
                      return builder.buildFuture();
                    })
                    .executes(this::executeKickServer)
                    .then(reasonNode.executes(this::executeKickServer));

    LiteralArgumentBuilder<CommandSource> serverNode = BrigadierCommand.literalArgumentBuilder("server")
            .requires(src -> src.getPermissionValue("velocity.command.gkick.server") == Tristate.TRUE)
            .then(serverNameNode);

    RequiredArgumentBuilder<CommandSource, String> playerNameNode =
            BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandUtils.suggestPlayer(server, ctx, builder))
                    .executes(this::executeKickPlayer)
                    .then(reasonNode.executes(this::executeKickPlayer));

    LiteralArgumentBuilder<CommandSource> playerNode = BrigadierCommand.literalArgumentBuilder("player")
            .requires(src -> src.getPermissionValue("velocity.command.gkick") == Tristate.TRUE)
            .then(playerNameNode);

    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(src -> src.getPermissionValue("velocity.command.gkick") == Tristate.TRUE)
            .executes(ctx -> {
              ctx.getSource().sendMessage(
                      Component.translatable("velocity.command.gkick.usage", Argument.string("0", label()))
              );
              return SINGLE_SUCCESS;
            })
            .then(allNode)
            .then(serverNode)
            .then(playerNode);

    return new BrigadierCommand(rootNode);
  }

  private Component parseReason(CommandContext<CommandSource> context) {
    if (!context.getArguments().containsKey("reason")) {
      return Component.translatable("velocity.command.gkick.reason");
    }
    return CommandUtils.deserializeComponent(
            context.getArgument("reason", String.class));
  }

  private int executeKickAll(CommandContext<CommandSource> context) {
    CommandSource source = context.getSource();
    Component reason = parseReason(context);

    Collection<VelocityClusterPlayer> allPlayers = server.getClusterPlayerService().getAllPlayers();

    if (allPlayers.isEmpty()) {
      boolean anyServerOnline = server.getAllServers().stream()
              .anyMatch(registeredServer -> !registeredServer.getPlayersConnected().isEmpty());

      if (!anyServerOnline && server.getClusterPlayerService().getAllPlayers().isEmpty()) {
        source.sendMessage(Component.translatable("velocity.command.gkick.all.offline"));
      } else {
        source.sendMessage(Component.translatable("velocity.command.gkick.none"));
      }
      return 0;
    }

    return kickCollection(context, allPlayers, reason, "velocity.command.gkick.all.bypass");
  }

  private int executeKickServer(CommandContext<CommandSource> context) {
    CommandSource source = context.getSource();
    String serverName = context.getArgument("name", String.class);
    Component reason = parseReason(context);

    Optional<RegisteredServer> registeredServer = server.getServer(serverName)
            .map(RegisteredServer.class::cast);

    if (registeredServer.isEmpty()) {
      source.sendMessage(Component.translatable("velocity.command.server-does-not-exist")
              .arguments(Argument.string("server", serverName)));
      return 0;
    }

    Collection<VelocityClusterPlayer> serverPlayers = server.getClusterPlayerService()
            .getAllPlayers()
            .stream()
            .filter(player -> Objects.requireNonNull(player.getServerName()).equalsIgnoreCase(serverName))
            .toList();

    if (serverPlayers.isEmpty()) {
      source.sendMessage(Component.translatable("velocity.command.gkick.server.offline")
              .arguments(Argument.string("server", serverName)));
      return 0;
    }

    return kickCollection(context, serverPlayers, reason, "velocity.command.gkick.server.bypass");
  }

  private int executeKickPlayer(CommandContext<CommandSource> context) {
    CommandSource source = context.getSource();
    String target = context.getArgument("name", String.class);
    Component reason = parseReason(context);

    Optional<VelocityClusterPlayer> maybePlayer = server
            .getClusterPlayerService().getPlayer(target);

    if (maybePlayer.isEmpty()) {
      source.sendMessage(
              CommandMessages.PLAYER_NOT_FOUND.arguments(
                      Argument.string("player", target))
      );
      return 0;
    }

    VelocityClusterPlayer player = maybePlayer.get();
    player.kick(reason);

    source.sendMessage(
            Component.translatable("velocity.command.gkick.message")
                    .arguments(Argument.string("0", player.getUsername()))
    );
    return SINGLE_SUCCESS;
  }

  private int kickCollection(CommandContext<CommandSource> context,
                             Collection<VelocityClusterPlayer> targets,
                             Component reason,
                             String bypassPermission) {
    CommandSource source = context.getSource();
    int kickedCount = 0;

    for (VelocityClusterPlayer player : targets) {
      if (bypassPermission != null) {
        boolean bypass = server.getPlayer(player.getUniqueId())
                .map(nativePlayer -> nativePlayer.hasPermission(bypassPermission))
                .orElse(false);
        if (bypass) {
          continue;
        }
      }

      player.kick(reason);
      kickedCount++;
    }

    if (kickedCount == 0) {
      source.sendMessage(Component.translatable("velocity.command.gkick.none"));
    } else {
      source.sendMessage(Component.translatable("velocity.command.gkick.all"));
    }
    return SINGLE_SUCCESS;
  }
}