/*
 * Copyright (C) 2020-2024 Velocity Contributors
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
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements the {@code /queueadmin} command.
 */
public class QueueAdminCommand {

  private final VelocityServer server;

  public QueueAdminCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(final boolean isQueueEnabled) {
    if (!isQueueEnabled) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand.literalArgumentBuilder("queueadmin")
        .requires(source -> source.getPermissionValue("velocity.queue.admin") == Tristate.TRUE)
        .then(BrigadierCommand.literalArgumentBuilder("listqueues")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.listqueues") == Tristate.TRUE)
             .executes(this::listQueues)
        ).then(BrigadierCommand.literalArgumentBuilder("pause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.pause") == Tristate.TRUE)
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                .suggests(suggestServer(server, "server"))
                .executes(this::pause)
            )
        ).then(BrigadierCommand.literalArgumentBuilder("unpause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.unpause") == Tristate.TRUE)
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                .suggests(suggestServer(server, "server"))
                .executes(this::unpause)
            )
        ).then(
          BrigadierCommand.literalArgumentBuilder("add")
              .requires(source -> source.getPermissionValue("velocity.queue.admin.add") == Tristate.TRUE)
              .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                  .suggests(this::suggestPlayer)
                  .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                      .suggests(suggestServer(server, "server"))
                      .executes(this::add)
            )
          )
        ).then(
          BrigadierCommand.literalArgumentBuilder("addall")
              .requires(source -> source.getPermissionValue("velocity.queue.admin.addall") == Tristate.TRUE)
              .then(BrigadierCommand.requiredArgumentBuilder("from", StringArgumentType.word())
                  .suggests(suggestServer(server, "from"))
                  .then(BrigadierCommand.requiredArgumentBuilder("to", StringArgumentType.word())
                      .suggests(suggestServer(server, "to"))
                      .executes(this::addAll)
                  )
              )
        ).then(BrigadierCommand.literalArgumentBuilder("remove")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.remove") == Tristate.TRUE)
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                .suggests(this::suggestPlayer)
                .executes(this::remove)
                .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(suggestServer(server, "server"))
                    .executes(this::remove)
                )
            )
        ).then(BrigadierCommand.literalArgumentBuilder("removeall")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.removeall") == Tristate.TRUE)
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                .suggests(suggestServer(server, "server"))
                .executes(this::removeAll)
            )
        );

    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  /**
   * Fetches a server from a string in a command context.
   *
   * @param server the proxy instance
   * @param ctx the command context
   * @param argName the name of the argument
   * @return the found server, or {@code null} if one couldn't be found
   */
  public static VelocityRegisteredServer getServer(VelocityServer server, CommandContext<CommandSource> ctx, String argName) {
    String serverName = ctx.getArgument(argName, String.class);
    Optional<RegisteredServer> serverOptional = server.getServer(serverName);

    if (serverOptional.isEmpty()) {
      ctx.getSource().sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(serverName)));
      return null;
    }

    return (VelocityRegisteredServer) serverOptional.get();
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

  private int listQueues(CommandContext<CommandSource> ctx) {
    CommandSource source = ctx.getSource();

    TranslatableComponent.Builder builder = Component.translatable("velocity.queue.command.listqueues.header")
        .append(Component.newline())
        .toBuilder();

    for (RegisteredServer server : this.server.getAllServers()) {
      VelocityRegisteredServer registeredServer = (VelocityRegisteredServer) server;
      ServerQueueStatus queueStatus = registeredServer.getQueueStatus();

      builder.append(queueStatus.createListComponent());
    }

    source.sendMessage(builder.build());
    return Command.SINGLE_SUCCESS;
  }

  private int pause(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = getServer(this.server, ctx, "server");

    if (server == null) {
      return -1;
    }

    Component serverName = Component.text(server.getServerInfo().getName());

    if (server.getQueueStatus().isPaused()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-paused")
          .arguments(serverName));
      return -1;
    }

    server.getQueueStatus().setPaused(true);

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.pause").arguments(serverName));
    server.getQueueStatus().broadcast(Component.translatable("velocity.queue.command.paused").arguments(serverName));

    return Command.SINGLE_SUCCESS;
  }

  private int unpause(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = getServer(this.server, ctx, "server");

    if (server == null) {
      return -1;
    }

    Component serverName = Component.text(server.getServerInfo().getName());

    if (!server.getQueueStatus().isPaused()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-paused")
          .arguments(serverName));
      return -1;
    }

    server.getQueueStatus().setPaused(false);

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.unpause").arguments(serverName));
    server.getQueueStatus().broadcast(Component.translatable("velocity.queue.command.unpaused").arguments(serverName));

    return Command.SINGLE_SUCCESS;
  }

  private int add(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = getServer(this.server, ctx, "server");

    if (server == null) {
      return -1;
    }

    Player player = getPlayer(this.server, ctx);

    if (player == null) {
      return -1;
    }

    if (server.getQueueStatus().isQueued(player)) {
      player.sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
          .arguments(
              Component.text(player.getUsername()),
              Component.text(server.getServerInfo().getName())
          )
      );
      return -1;
    }

    server.getQueueStatus().queueWithIndication(player);
    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.added")
        .arguments(
            Component.text(player.getUsername()),
            Component.text(server.getServerInfo().getName())
        ));

    return Command.SINGLE_SUCCESS;
  }

  private int addAll(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer from = getServer(this.server, ctx, "from");

    if (from == null) {
      return -1;
    }

    VelocityRegisteredServer to = getServer(this.server, ctx, "to");

    if (to == null) {
      return -1;
    }

    Collection<Player> players = from.getPlayersConnected();

    if (players.isEmpty()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.addall-no-players-queued", NamedTextColor.RED)
          .arguments(
              Component.text(from.getServerInfo().getName()),
              Component.text(to.getServerInfo().getName())
          )
      );
      return -1;
    }

    for (Player player : players) {
      to.getQueueStatus().queueWithIndication(player);
    }

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.addedall-player" + (players.size() == 1 ? "" : "s"))
        .arguments(
            Component.text(players.size()),
            Component.text(to.getServerInfo().getName())
        )
    );

    return Command.SINGLE_SUCCESS;
  }

  private int remove(CommandContext<CommandSource> ctx) {
    Player player = getPlayer(this.server, ctx);

    if (player == null) {
      return -1;
    }

    List<RegisteredServer> servers;
    boolean serverWasPassed = false;

    if (ctx.getArguments().containsKey("server")) {
      serverWasPassed = true;
      VelocityRegisteredServer registeredServer = getServer(server, ctx, "server");

      if (registeredServer == null) {
        return -1;
      }

      servers = List.of(registeredServer);
    } else {
      servers = new ArrayList<>(this.server.getAllServers());
    }

    boolean removedAny = false;

    for (RegisteredServer server : servers) {
      VelocityRegisteredServer registeredServer = (VelocityRegisteredServer) server;
      if (registeredServer.getQueueStatus().dequeue(player)) {
        removedAny = true;
      }
    }

    if (!removedAny) {
      player.sendMessage(Component.translatable("velocity.queue.error.not-in-queue.other")
          .arguments(Component.text(player.getUsername()))
      );
      return -1;
    }

    if (serverWasPassed) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.removed").arguments(
          Component.text(player.getUsername()),
          Component.text(servers.get(0).getServerInfo().getName())
      ));
    } else {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.removed.all").arguments(
          Component.text(player.getUsername()),
          Component.text(servers.get(0).getServerInfo().getName())
      ));
    }

    return Command.SINGLE_SUCCESS;
  }

  private int removeAll(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = getServer(this.server, ctx, "server");

    if (server == null) {
      return -1;
    }

    Collection<Player> players = server.getPlayersConnected();

    if (players.isEmpty()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.removeall-no-players-queued")
          .arguments(Component.text(server.getServerInfo().getName())));
      return -1;
    }

    int amountDequeued = 0;

    for (Player player : players) {
      if (server.getQueueStatus().dequeue(player)) {
        amountDequeued += 1;
      }
    }

    if (amountDequeued == 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.removeall-no-players-queued")
          .arguments(Component.text(server.getServerInfo().getName())));
      return -1;
    }

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.removedall-player" + (amountDequeued == 1 ? "" : "s"))
        .arguments(
            Component.text(amountDequeued),
            Component.text(server.getServerInfo().getName())
        )
    );

    return Command.SINGLE_SUCCESS;
  }

  private CompletableFuture<Suggestions> suggestPlayer(CommandContext<CommandSource> ctx, SuggestionsBuilder builder) {
    final String argument = ctx.getArguments().containsKey("player")
        ? ctx.getArgument("player", String.class)
        : "";
    for (final Player player : server.getAllPlayers()) {
      final String playerName = player.getUsername();
      if (playerName.regionMatches(true, 0, argument, 0, argument.length())) {
        builder.suggest(playerName);
      }
    }

    return builder.buildFuture();
  }

  /**
   * Generates a suggestion provider to complete the name of a server.
   *
   * @param server the proxy server
   * @param argName the name of the string argument to complete
   * @return a suggestion provider that completes a server name
   */
  public static SuggestionProvider<CommandSource> suggestServer(VelocityServer server, String argName) {
    return (ctx, builder) -> {
      final String argument = ctx.getArguments().containsKey(argName)
          ? StringArgumentType.getString(ctx, argName)
          : "";

      for (final RegisteredServer sv : server.getAllServers()) {
        final String serverName = sv.getServerInfo().getName();

        if (server.getConfiguration().getQueue().getNoQueueServers().contains(serverName)) {
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
}
