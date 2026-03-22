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
import com.velocityctd.proxy.cluster.ClusterPlayer;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements Velocity's {@code /send} command.
 */
public class SendCommand implements BuiltinCommand {

  private static final String SELECTOR_ARG = "selector";
  private static final String TARGET_ARG = "target";

  private static final String ALL = "all";
  private static final String CURRENT = "current";
  private static final char SERVER_PREFIX = '+';

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
                .suggests(this::suggestPlayers)
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

  private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSource> ctx, SuggestionsBuilder builder) {
    String input = builder.getRemaining();

    for (String name : server.getClusterPlayerService().getPlayerNames()) {
      if (startsWithIgnoreCase(name, input)) {
        builder.suggest(name);
      }
    }

    if (startsWithIgnoreCase(ALL, input)) {
      builder.suggest(ALL);
    }

    if (ctx.getSource() instanceof ConnectedPlayer && startsWithIgnoreCase(CURRENT, input)) {
      builder.suggest(CURRENT);
    }

    if (input.isEmpty() || input.charAt(0) == SERVER_PREFIX) {
      String serverPart = input.isEmpty() ? "" : input.substring(1);
      for (VelocityRegisteredServer rs : server.getAllServers()) {
        String name = rs.getServerInfo().getName();
        if (startsWithIgnoreCase(name, serverPart)) {
          builder.suggest(SERVER_PREFIX + name);
        }
      }
    }

    return builder.buildFuture();
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

    // all
    if (equalsIgnoreCase(selector, ALL)) {
      return sendAll(ctx, target);
    }

    // current
    if (equalsIgnoreCase(selector, CURRENT)) {
      if (!(ctx.getSource() instanceof ConnectedPlayer source)) {
        ctx.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
        return 0;
      }

      Collection<VelocityRegisteredServer> fromServers = lookupServers(selector, source);
      if (fromServers.isEmpty()) {
        return 0; // caller has no current server
      }

      String fromName = fromServers.iterator().next().getServerInfo().getName();
      String toName = target.getServerInfo().getName();
      if (equalsIgnoreCase(fromName, toName)) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.send-same-server"));
        return 0;
      }

      return sendFromServers(ctx, fromServers, target);
    }

    // +pattern
    if (!selector.isEmpty() && selector.charAt(0) == SERVER_PREFIX) {
      String pattern = selector.substring(1);
      Collection<VelocityRegisteredServer> fromServers = lookupServers(selector, null);
      if (fromServers.isEmpty()) {
        ctx.getSource().sendMessage(
            CommandMessages.SERVER_DOES_NOT_EXIST.arguments(Component.text(pattern))
        );
        return 0;
      }

      return sendFromServers(ctx, fromServers, target);
    }

    // single player
    return sendSinglePlayer(ctx, selector, target);
  }

  private Collection<VelocityRegisteredServer> lookupServers(String selector, @Nullable ConnectedPlayer sourcePlayer) {
    if (equalsIgnoreCase(selector, CURRENT)) {
      if (sourcePlayer == null) {
        return Collections.emptySet();
      }

      return sourcePlayer.getCurrentServer()
          .map(VelocityServerConnection::getServer)
          .map(Collections::singleton)
          .orElseGet(Collections::emptySet);
    }

    if (!selector.isEmpty() && selector.charAt(0) == SERVER_PREFIX) {
      return lookupServersByName(selector.substring(1));
    }

    return Collections.emptySet();
  }

  private Collection<VelocityRegisteredServer> lookupServersByName(String input) {
    String needle = input.toLowerCase();
    if (needle.isEmpty()) {
      return Collections.emptySet();
    }

    VelocityRegisteredServer exact = null;
    List<VelocityRegisteredServer> prefix = new ArrayList<>();
    List<VelocityRegisteredServer> contains = new ArrayList<>();

    for (VelocityRegisteredServer rs : server.getAllServers()) {
      String name = rs.getServerInfo().getName();
      String lower = name.toLowerCase();

      if (lower.equals(needle)) {
        exact = rs;
        break;
      }

      if (lower.startsWith(needle)) {
        prefix.add(rs);
      } else if (lower.contains(needle)) {
        contains.add(rs);
      }
    }

    if (exact != null) {
      return Collections.singleton(exact);
    } else if (!prefix.isEmpty()) {
      return prefix;
    } else {
      return contains; // may be empty
    }
  }

  private int sendAll(CommandContext<CommandSource> ctx, VelocityRegisteredServer target) {
    String toName = target.getServerInfo().getName();
    Collection<ClusterPlayer> players = server.getClusterPlayerService().getAllPlayers();

    for (ClusterPlayer player : players) {
      player.move(toName);
    }

    int count = players.size();
    ctx.getSource().sendMessage(
        Component.translatable(count == 1 ? "velocity.command.send-all-singular" : "velocity.command.send-all-plural")
            .arguments(
                Argument.numeric("count", count),
                Argument.string("server", toName)
            )
    );

    return Command.SINGLE_SUCCESS;
  }

  private int sendFromServers(CommandContext<CommandSource> ctx,
                              Collection<VelocityRegisteredServer> fromServers,
                              VelocityRegisteredServer target) {
    String toName = target.getServerInfo().getName();
    boolean anyMessage = false;
    int skippedSameTarget = 0;

    for (VelocityRegisteredServer from : fromServers) {
      String fromName = from.getServerInfo().getName();

      // If +pattern matches the target server too, just skip that one (unless it's the only match)
      if (equalsIgnoreCase(fromName, toName)) {
        skippedSameTarget++;
        continue;
      }

      Collection<ClusterPlayer> players = server.getClusterPlayerService().getPlayersOnServer(fromName);
      if (players.isEmpty()) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.send-server-none")
            .arguments(
                Argument.string("server", fromName),
                Argument.string("to", toName)
            ));
        anyMessage = true;
        continue;
      }

      for (ClusterPlayer player : players) {
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
      anyMessage = true;
    }

    if (!anyMessage && skippedSameTarget > 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.send-same-server"));
      return 0;
    }

    return Command.SINGLE_SUCCESS;
  }

  private int sendSinglePlayer(CommandContext<CommandSource> ctx, String playerInput, VelocityRegisteredServer target) {
    String toName = target.getServerInfo().getName();

    Optional<ClusterPlayer> maybePlayer = server.getClusterPlayerService().getPlayer(playerInput);
    if (maybePlayer.isEmpty()) {
      ctx.getSource().sendMessage(CommandMessages.PLAYER_NOT_FOUND
          .arguments(
              Argument.string("player", playerInput)
          ));

      return 0;
    }

    ClusterPlayer player = maybePlayer.get();
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
