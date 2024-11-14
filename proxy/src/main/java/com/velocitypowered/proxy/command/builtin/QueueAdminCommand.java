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
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.redis.multiproxy.RedisQueueLeaveRequest;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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

    final LiteralCommandNode<CommandSource> listQueues = BrigadierCommand.literalArgumentBuilder("listqueues")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.listqueues") == Tristate.TRUE)
            .executes(this::listQueues)
            .build();

    final LiteralCommandNode<CommandSource> pause = BrigadierCommand.literalArgumentBuilder("pause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.pause") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.pause"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(VelocityCommands.suggestServer(server, "server", false))
                    .executes(this::pause))
            .build();

    final LiteralCommandNode<CommandSource> unpause = BrigadierCommand.literalArgumentBuilder("unpause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.unpause") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.unpause"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(VelocityCommands.suggestServer(server, "server", false))
                    .executes(this::unpause)
            )
            .build();

    final LiteralCommandNode<CommandSource> add = BrigadierCommand.literalArgumentBuilder("add")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.add") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.add"))
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, true))
                    .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.add"))
                    .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                            .suggests(VelocityCommands.suggestServer(server, "server", false))
                            .executes(this::add)))
            .build();

    final LiteralCommandNode<CommandSource> addall = BrigadierCommand.literalArgumentBuilder("addall")
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
            .build();

    final LiteralCommandNode<CommandSource> remove = BrigadierCommand.literalArgumentBuilder("remove")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.remove") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.remove"))
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, true))
                    .executes(this::remove)
                    .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                            .suggests(VelocityCommands.suggestServer(server, "server", false))
                            .executes(this::remove)
                    )
            )
            .build();

    final LiteralCommandNode<CommandSource> removeall = BrigadierCommand.literalArgumentBuilder("removeall")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.removeall") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.removeall"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(VelocityCommands.suggestServer(server, "server", false))
                    .executes(this::removeAll)
            )
            .build();

    final List<LiteralCommandNode<CommandSource>> commands = List
            .of(listQueues, pause, unpause, add, addall, remove, removeall);
    BrigadierCommand command = new BrigadierCommand(
            commands.stream()
                    .reduce(
                            BrigadierCommand.literalArgumentBuilder("queueadmin")
                                    .executes(ctx -> {
                                      final CommandSource source = ctx.getSource();
                                      final String availableCommands = commands.stream()
                                              .filter(e -> e.getRequirement().test(source))
                                              .map(LiteralCommandNode::getName)
                                              .collect(Collectors.joining("|"));
                                      final String commandText = "/queueadmin <%s>".formatted(availableCommands);
                                      source.sendMessage(Component.text(commandText, NamedTextColor.RED));
                                      return Command.SINGLE_SUCCESS;
                                    })
                                    .requires(commands.stream()
                                            .map(CommandNode::getRequirement)
                                            .reduce(Predicate::or)
                                            .orElseThrow()),
                            ArgumentBuilder::then,
                            ArgumentBuilder::then
                    )
    );

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
      ServerQueueStatus queueStatus = this.server.getQueueManager()
              .getQueue(registeredServer.getServerInfo().getName());

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

    this.server.getQueueManager().queue(player, server);
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
      server.getQueueManager().queue(player, to);
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
    if (ctx.getArguments().containsKey("server")) {
      VelocityRegisteredServer registeredServer = VelocityCommands.getServer(server, ctx, "server", false);

      if (registeredServer == null) {
        return -1;
      }

      servers = List.of(registeredServer);
    } else {
      servers = new ArrayList<>(this.server.getAllServers());
    }

    for (RegisteredServer server : servers) {
      this.server.getRedisManager().send(new RedisQueueLeaveRequest(player.getUniqueId(),
              server.getServerInfo().getName(), false));
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
      if (server.getQueueStatus().dequeue(player.getUniqueId())) {
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
