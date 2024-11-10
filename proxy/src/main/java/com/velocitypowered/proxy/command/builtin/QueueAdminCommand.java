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
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
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

    final List<String> aliases = server.getConfiguration().getQueue().getQueueAdminAliases();

    if (aliases.isEmpty()) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand.literalArgumentBuilder(aliases.remove(0))
        .requires(source -> source.getPermissionValue("velocity.queue.admin") == Tristate.TRUE)
        .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin"))
        .then(BrigadierCommand.literalArgumentBuilder("listqueues")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.listqueues") == Tristate.TRUE)
            .executes(this::listQueues)
        ).then(BrigadierCommand.literalArgumentBuilder("pause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.pause") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.pause"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                .suggests(VelocityCommands.suggestServer(server, "server", false))
                .executes(this::pause)
            )
        ).then(BrigadierCommand.literalArgumentBuilder("unpause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.unpause") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.unpause"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                .suggests(VelocityCommands.suggestServer(server, "server", false))
                .executes(this::unpause)
            )
        ).then(
          BrigadierCommand.literalArgumentBuilder("add")
              .requires(source -> source.getPermissionValue("velocity.queue.admin.add") == Tristate.TRUE)
              .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.add"))
              .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                  .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, false))
                  .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.add"))
                  .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                      .suggests(VelocityCommands.suggestServer(server, "server", false))
                      .executes(this::add)
            )
          )
        ).then(
          BrigadierCommand.literalArgumentBuilder("addall")
              .requires(source -> source.getPermissionValue("velocity.queue.admin.addall") == Tristate.TRUE)
              .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.addall"))
              .then(BrigadierCommand.requiredArgumentBuilder("from", StringArgumentType.word())
                  .suggests(VelocityCommands.suggestServer(server, "from", false))
                  .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.addall"))
                  .then(BrigadierCommand.requiredArgumentBuilder("to", StringArgumentType.word())
                      .suggests(VelocityCommands.suggestServer(server, "to", false))
                      .executes(this::addAll)
                  )
              )
        ).then(BrigadierCommand.literalArgumentBuilder("remove")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.remove") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.remove"))
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, false))
                .executes(this::remove)
                .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(VelocityCommands.suggestServer(server, "server", false))
                    .executes(this::remove)
                )
            )
        ).then(BrigadierCommand.literalArgumentBuilder("removeall")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.removeall") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.removeall"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                .suggests(VelocityCommands.suggestServer(server, "server", false))
                .executes(this::removeAll)
            )
        );

    final BrigadierCommand command = new BrigadierCommand(rootNode);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .aliases(aliases.toArray(new String[0]))
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int listQueues(final CommandContext<CommandSource> ctx) {
    CommandSource source = ctx.getSource();
    source.sendMessage(Component.translatable("velocity.queue.command.listqueues.header"));

    for (RegisteredServer server : this.server.getAllServers()) {
      VelocityRegisteredServer registeredServer = (VelocityRegisteredServer) server;
      ServerQueueStatus queueStatus = registeredServer.getQueueStatus();

      source.sendMessage(queueStatus.createListComponent());
    }

    return Command.SINGLE_SUCCESS;
  }

  private int pause(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", false);

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

  private int unpause(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", false);

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

  private int add(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", false);

    if (server == null) {
      return -1;
    }

    Player player = VelocityCommands.getPlayer(this.server, ctx);

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

    this.server.getQueueManager().queueWithIndication(player, server);
    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.added")
        .arguments(
            Component.text(player.getUsername()),
            Component.text(server.getServerInfo().getName())
        ));

    return Command.SINGLE_SUCCESS;
  }

  private int addAll(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer from = VelocityCommands.getServer(this.server, ctx, "from", false);

    if (from == null) {
      return -1;
    }

    VelocityRegisteredServer to = VelocityCommands.getServer(this.server, ctx, "to", false);

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
      server.getQueueManager().queueWithIndication(player, to);
    }

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.addedall-player" + (players.size() == 1 ? "" : "s"))
        .arguments(
            Component.text(players.size()),
            Component.text(to.getServerInfo().getName())
        )
    );

    return Command.SINGLE_SUCCESS;
  }

  private int remove(final CommandContext<CommandSource> ctx) {
    Player player = VelocityCommands.getPlayer(this.server, ctx);

    if (player == null) {
      return -1;
    }

    List<RegisteredServer> servers;
    boolean serverWasPassed = false;

    if (ctx.getArguments().containsKey("server")) {
      serverWasPassed = true;
      VelocityRegisteredServer registeredServer = VelocityCommands.getServer(server, ctx, "server", false);

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

  private int removeAll(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", false);

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
}
