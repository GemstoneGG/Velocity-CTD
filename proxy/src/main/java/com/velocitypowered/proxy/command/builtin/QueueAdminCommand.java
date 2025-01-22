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
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.queue.ServerQueueEntry;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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

    final List<String> aliases = server.getConfiguration().getQueue().getQueueAdminAliases();

    if (aliases.isEmpty()) {
      return;
    }

    final LiteralCommandNode<CommandSource> listQueues = BrigadierCommand.literalArgumentBuilder("listqueues")
        .requires(source -> source.getPermissionValue("velocity.queue.admin.listqueues") == Tristate.TRUE)
        .executes(this::listQueues)
        .build();

    final LiteralCommandNode<CommandSource> list = BrigadierCommand.literalArgumentBuilder("list")
        .requires(source -> source.getPermissionValue("velocity.queue.admin.list") == Tristate.TRUE)
        .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.list"))
        .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
            .suggests((ctx, builder) -> {
              final String argument = ctx.getArguments().containsKey("server")
                  ? ctx.getArgument("server", String.class)
                  : "";

              if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
                builder.suggest("all");
              }

              if ("current".regionMatches(true, 0, argument, 0, argument.length())) {
                builder.suggest("current");
              }

              for (RegisteredServer s : server.getAllServers()) {
                if (this.server.getConfiguration().getQueue().getNoQueueServers()
                    .contains(s.getServerInfo().getName())) {
                  continue;
                }

                if (s.getServerInfo().getName().regionMatches(true, 0, argument, 0, argument.length())) {
                  builder.suggest(s.getServerInfo().getName());
                }
              }

              return builder.buildFuture();
            })
            .executes(this::list))
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
            .executes(this::unpause))
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
            .suggests(VelocityCommands.suggestServer(server, "from", true))
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.addall"))
            .then(BrigadierCommand.requiredArgumentBuilder("to", StringArgumentType.word())
                .suggests(VelocityCommands.suggestServer(server, "to", false))
                .executes(this::addAll)))
        .build();

    final LiteralCommandNode<CommandSource> remove = BrigadierCommand.literalArgumentBuilder("remove")
        .requires(source -> source.getPermissionValue("velocity.queue.admin.remove") == Tristate.TRUE)
        .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.remove"))
        .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
            .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, true))
            .executes(this::remove)
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                .suggests(VelocityCommands.suggestServer(server, "server", false))
                .executes(this::remove)))
        .build();

    final LiteralCommandNode<CommandSource> removeall = BrigadierCommand.literalArgumentBuilder("removeall")
        .requires(source -> source.getPermissionValue("velocity.queue.admin.removeall") == Tristate.TRUE)
        .executes(ctx -> VelocityCommands.emitUsage(ctx, "queueadmin.removeall"))
        .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
            .suggests(VelocityCommands.suggestServer(server, "server", false))
            .executes(this::removeAll))
        .build();

    final List<LiteralCommandNode<CommandSource>> commands = List
        .of(listQueues, list, pause, unpause, add, addall, remove, removeall);
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
                ArgumentBuilder::then));

    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .aliases(aliases.toArray(new String[0]))
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command);
  }

  private int listQueues(final CommandContext<CommandSource> ctx) {
    CommandSource source = ctx.getSource();
    source.sendMessage(Component.translatable("velocity.queue.command.listqueues.header"));

    for (RegisteredServer server : this.server.getAllServers()) {
      if (this.server.getConfiguration().getQueue().getNoQueueServers().contains(server.getServerInfo().getName())) {
        continue;
      }

      VelocityRegisteredServer registeredServer = (VelocityRegisteredServer) server;
      ServerQueueStatus queueStatus = this.server.getQueueManager()
          .getQueue(registeredServer.getServerInfo().getName());

      source.sendMessage(queueStatus.createListComponent());
    }

    return Command.SINGLE_SUCCESS;
  }

  private int list(final CommandContext<CommandSource> ctx) {
    String serverName = ctx.getArgument("server", String.class);
    VelocityRegisteredServer server = (VelocityRegisteredServer) this.server.getServer(ctx
        .getArgument("server", String.class)).orElse(null);

    if (serverName.equalsIgnoreCase("current")) {
      if (!(ctx.getSource() instanceof Player p)) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.players-only"));
        return -1;
      }
      ServerConnection connection = p.getCurrentServer().orElse(null);
      if (connection == null) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.current-not-connected"));
        return -1;
      }
      server = (VelocityRegisteredServer) this.server.getServer(connection.getServerInfo().getName()).orElse(null);
    }

    if (serverName.equalsIgnoreCase("all")) {
      Set<UUID> uniquePlayers = new HashSet<>();

      for (ServerQueueStatus status : this.server.getQueueManager().getAll()) {
        for (ServerQueueEntry entry : status.getAllEntries()) {
          uniquePlayers.add(entry.getPlayer());
        }
      }

      int uniquePlayerCount = uniquePlayers.size();

      if (uniquePlayerCount == 1) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.global-singular")
            .arguments(Component.text(uniquePlayerCount)));
      } else {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.global-plural")
            .arguments(Component.text(uniquePlayerCount)));
      }

      for (RegisteredServer s : this.server.getAllServers()) {
        if (this.server.getConfiguration().getQueue().getNoQueueServers().contains(s.getServerInfo().getName())) {
          continue;
        }

        VelocityRegisteredServer velocityRegisteredServer = (VelocityRegisteredServer) s;
        List<Component> players = new ArrayList<>();

        for (ServerQueueEntry entry : velocityRegisteredServer.getQueueStatus().getAllEntries()) {
          players.add(Component.text(entry.getUsername()));
        }

        players.stream()
            .reduce((a, b) -> a.append(Component.text(", ")).append(b))
            .ifPresent(playerList -> {
              final TranslatableComponent.Builder builder = Component.translatable()
                  .key("velocity.queue.command.list.server")
                  .arguments(
                      Component.text(s.getServerInfo().getName()),
                      Component.text(players.size()),
                      playerList);
              ctx.getSource().sendMessage(builder.build());
            });

      }

      return -1;
    } else if (server == null) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.server-does-not-exist")
          .arguments(Component.text(serverName)));
      return -1;
    }

    List<Component> players = new ArrayList<>();

    if (this.server.getConfiguration().getQueue().getNoQueueServers().contains(server.getServerInfo().getName())) {
      String newName = serverName;

      if (serverName.equalsIgnoreCase("current")) {
        ServerConnection conn = ((Player) ctx.getSource()).getCurrentServer().orElse(null);
        if (conn != null) {
          newName = conn.getServerInfo().getName();
        }
      }

      ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.disabled-queue")
          .arguments(Component.text(newName)));
      return -1;
    }

    for (ServerQueueEntry entry : server.getQueueStatus().getAllEntries()) {
      players.add(Component.text(entry.getUsername()));
    }

    VelocityRegisteredServer finalServer = server;
    players.stream()
        .reduce((a, b) -> a.append(Component.text(", ")).append(b))
        .ifPresentOrElse(playerList -> {
          final TranslatableComponent.Builder builder = Component.translatable()
              .key("velocity.queue.command.list.server")
              .arguments(
                  Component.text(finalServer.getServerInfo().getName()),
                  Component.text(players.size()),
                  playerList);
          ctx.getSource().sendMessage(builder.build());
        }, () -> {
          if (players.isEmpty()) {
            ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.empty")
                .arguments(Component.text(finalServer.getServerInfo().getName())));
          }
        });

    return Command.SINGLE_SUCCESS;
  }

  private int pause(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", false);
    if (server == null) {
      return -1;
    }

    Component serverName = Component.text(server.getServerInfo().getName());

    if (server.getQueueStatus().isPaused()) {
      if (this.server.getMultiProxyHandler().isRedisEnabled()) {
        this.server.getRedisManager().removePausedQueue(server.getServerInfo().getName());
      } else {
        server.getQueueStatus().setPaused(false);
      }

      ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.unpause").arguments(serverName));
      server.getQueueStatus()
          .broadcast(Component.translatable("velocity.queue.command.unpaused").arguments(serverName));
      return Command.SINGLE_SUCCESS;
    } else {

      if (this.server.getMultiProxyHandler().isRedisEnabled()) {
        this.server.getRedisManager().addPausedQueue(server.getServerInfo().getName());
      } else {
        server.getQueueStatus().setPaused(true);
      }

      ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.pause").arguments(serverName));
      server.getQueueStatus().broadcast(Component.translatable("velocity.queue.command.paused").arguments(serverName));
    }

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

    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      this.server.getRedisManager().removePausedQueue(server.getServerInfo().getName());
    } else {
      server.getQueueStatus().setPaused(false);
    }

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.unpause").arguments(serverName));
    server.getQueueStatus().broadcast(Component.translatable("velocity.queue.command.unpaused").arguments(serverName));

    return Command.SINGLE_SUCCESS;
  }

  private int add(final CommandContext<CommandSource> ctx) {
    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      return addRedis(ctx);
    }

    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", false);
    String playerName = ctx.getArgument("player", String.class);

    if (server == null) {
      return -1;
    }

    Player player = this.server.getPlayer(playerName).orElse(null);
    if (player == null) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
          .arguments(Component.text(playerName)));
      return -1;
    }

    if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
      for (ServerQueueStatus status : this.server.getQueueManager().getAll()) {
        if (status.isQueued(player.getUniqueId())) {
          ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
              .arguments(
                  Component.text(player.getUsername()),
                  Component.text(status.getServerName())));
          return -1;
        }
      }
    }

    ServerConnection conn = player.getCurrentServer().orElse(null);
    if (conn != null && conn.getServerInfo().getName().equalsIgnoreCase(server.getServerInfo().getName())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-connected")
          .arguments(Component.text(player.getUsername())));
      return -1;
    }

    if (server.getQueueStatus().isQueued(player.getUniqueId())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
          .arguments(
              Component.text(player.getUsername()),
              Component.text(server.getServerInfo().getName())));
      return -1;
    }

    server.getQueueStatus().queue(player.getUniqueId(), player.getQueuePriority(server.getServerInfo().getName()),
        player.hasPermission("velocity.queue.full.bypass"),
        player.hasPermission("velocity.queue.bypass"));

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.added")
        .arguments(
            Component.text(player.getUsername()),
            Component.text(server.getServerInfo().getName())));

    return Command.SINGLE_SUCCESS;
  }

  private int addRedis(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", false);
    String playerName = ctx.getArgument("player", String.class);

    if (server == null) {
      return -1;
    }

    RemotePlayerInfo player = this.server.getMultiProxyHandler().getPlayerInfo(playerName);
    if (player == null) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
          .arguments(Component.text(playerName)));
      return -1;
    }

    if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
      for (ServerQueueStatus status : this.server.getQueueManager().getAll()) {
        if (status.isQueued(player.getUuid())) {
          ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
              .arguments(
                  Component.text(player.getUsername()),
                  Component.text(status.getServerName())));
          return -1;
        }
      }
    }

    String conn = player.getServerName();
    if (conn != null && conn.equalsIgnoreCase(server.getServerInfo().getName())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-connected")
          .arguments(Component.text(player.getUsername())));
      return -1;
    }

    if (server.getQueueStatus().isQueued(player.getUuid())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
          .arguments(
              Component.text(player.getUsername()),
              Component.text(server.getServerInfo().getName())));
      return -1;
    }

    server.getQueueStatus().queue(player.getUuid(), player.getQueuePriority().get(server.getServerInfo().getName()),
        player.isFullQueueBypass(),
        player.isQueueBypass());

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.added")
        .arguments(
            Component.text(player.getUsername()),
            Component.text(server.getServerInfo().getName())));

    return Command.SINGLE_SUCCESS;
  }

  private int addAll(final CommandContext<CommandSource> ctx) {
    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      return addAllRedis(ctx);
    }

    VelocityRegisteredServer from = VelocityCommands.getServer(this.server, ctx, "from", true);
    if (from == null) {
      return -1;
    }

    VelocityRegisteredServer to = VelocityCommands.getServer(this.server, ctx, "to", false);
    if (to == null) {
      return -1;
    }

    if (to.getServerInfo().getName().equalsIgnoreCase(from.getServerInfo().getName())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.same-server-queue"));
      return -1;
    }

    List<Player> connected = new ArrayList<>();
    for (Player player : this.server.getAllPlayers()) {
      ServerConnection conn = player.getCurrentServer().orElse(null);
      if (conn != null && conn.getServerInfo().getName().equalsIgnoreCase(from.getServerInfo().getName())) {
        if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
          boolean alreadyQueued = this.server.getQueueManager().getAll().stream()
              .anyMatch(status -> status.isQueued(player.getUniqueId()));
          if (alreadyQueued) {
            continue;
          }
        }
        if (!to.getQueueStatus().isQueued(player.getUniqueId())) {
          connected.add(player);
        }
      }
    }

    if (connected.isEmpty()) {
      ctx.getSource()
          .sendMessage(Component.translatable("velocity.queue.error.addall-no-players-queued", NamedTextColor.RED)
              .arguments(
                  Component.text(from.getServerInfo().getName()),
                  Component.text(to.getServerInfo().getName())));
      return -1;
    }
    for (Player player : connected) {
      to.getQueueStatus().queue(player.getUniqueId(), player.getQueuePriority(to.getServerInfo().getName()),
          player.hasPermission("velocity.queue.full.bypass"),
          player.hasPermission("velocity.queue.bypass")
      );
    }

    int connectedSize = connected.size();

    ctx.getSource()
        .sendMessage(Component.translatable("velocity.queue.command.addedall-player" + (connectedSize == 1 ? "" : "s"))
            .arguments(
                Component.text(connectedSize),
                Component.text(to.getServerInfo().getName())));

    return Command.SINGLE_SUCCESS;
  }

  private int addAllRedis(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer from = VelocityCommands.getServer(this.server, ctx, "from", true);
    if (from == null) {
      return -1;
    }

    VelocityRegisteredServer to = VelocityCommands.getServer(this.server, ctx, "to", false);
    if (to == null) {
      return -1;
    }

    if (to.getServerInfo().getName().equalsIgnoreCase(from.getServerInfo().getName())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.same-server-queue"));
      return -1;
    }

    List<RemotePlayerInfo> connected = new ArrayList<>();
    for (RemotePlayerInfo player : this.server.getMultiProxyHandler().getAllPlayers()) {
      String conn = player.getServerName();
      if (conn != null && conn.equalsIgnoreCase(from.getServerInfo().getName())) {
        if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
          boolean alreadyQueued = this.server.getQueueManager().getAll().stream()
              .anyMatch(status -> status.isQueued(player.getUuid()));
          if (alreadyQueued) {
            continue;
          }
        }
        if (!to.getQueueStatus().isQueued(player.getUuid())) {
          connected.add(player);
        }
      }
    }

    if (connected.isEmpty()) {
      ctx.getSource()
          .sendMessage(Component.translatable("velocity.queue.error.addall-no-players-queued", NamedTextColor.RED)
              .arguments(
                  Component.text(from.getServerInfo().getName()),
                  Component.text(to.getServerInfo().getName())));
      return -1;
    }
    for (RemotePlayerInfo player : connected) {
      to.getQueueStatus().queue(player.getUuid(), player.getQueuePriority().get(to.getServerInfo().getName()),
          player.isFullQueueBypass(),
          player.isQueueBypass());
    }

    int connectedSize = connected.size();

    ctx.getSource()
        .sendMessage(Component.translatable("velocity.queue.command.addedall-player" + (connectedSize == 1 ? "" : "s"))
            .arguments(
                Component.text(connectedSize),
                Component.text(to.getServerInfo().getName())));

    return Command.SINGLE_SUCCESS;
  }

  private int remove(final CommandContext<CommandSource> ctx) {
    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      return removeRedis(ctx);
    }

    String playerName = ctx.getArgument("player", String.class);

    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      if (this.server.getMultiProxyHandler().isPlayerOnline(playerName)) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
            .arguments(Component.text(playerName)));
        return -1;
      }
    } else {
      if (this.server.getPlayer(playerName).isEmpty()) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
            .arguments(Component.text(playerName)));
        return -1;
      }
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

    Player player = this.server.getPlayer(playerName).orElse(null);
    if (player == null) {
      return -1;
    }

    boolean handledSpecific = false;
    int amountDone = 0;

    for (RegisteredServer s : servers) {
      VelocityRegisteredServer velocityRegisteredServer = (VelocityRegisteredServer) s;
      if (servers.size() == 1 && velocityRegisteredServer.getQueueStatus().isQueued(player.getUniqueId())) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.remove-success")
            .arguments(Component.text(player.getUsername()),
                Component.text(velocityRegisteredServer.getServerInfo().getName())));
        handledSpecific = true;
      } else if (servers.size() == 1) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-in-queue.other.specific")
            .arguments(Component.text(player.getUsername()), Component.text(s.getServerInfo().getName())));
        handledSpecific = true;
      }

      if (velocityRegisteredServer.getQueueStatus().isQueued(player.getUniqueId())) {
        amountDone++;
        velocityRegisteredServer.getQueueStatus().dequeue(player.getUniqueId(), false);
      }
    }

    if (!handledSpecific && amountDone == 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-in-queue.other")
          .arguments(Component.text(player.getUsername())));
      return Command.SINGLE_SUCCESS;
    }

    if (servers.size() > 1) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.remove-all-success")
          .arguments(Component.text(player.getUsername())));
    }
    return Command.SINGLE_SUCCESS;
  }

  private int removeRedis(final CommandContext<CommandSource> ctx) {
    String playerName = ctx.getArgument("player", String.class);

    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      if (this.server.getMultiProxyHandler().isPlayerOnline(playerName)) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
            .arguments(Component.text(playerName)));
        return -1;
      }
    } else {
      if (this.server.getPlayer(playerName).isEmpty()) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
            .arguments(Component.text(playerName)));
        return -1;
      }
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

    RemotePlayerInfo player = this.server.getMultiProxyHandler().getPlayerInfo(playerName);
    if (player == null) {
      return -1;
    }

    boolean handledSpecific = false;
    int amountDone = 0;

    for (RegisteredServer s : servers) {
      VelocityRegisteredServer velocityRegisteredServer = (VelocityRegisteredServer) s;
      if (servers.size() == 1 && velocityRegisteredServer.getQueueStatus().isQueued(player.getUuid())) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.remove-success")
            .arguments(Component.text(player.getUsername()),
                Component.text(velocityRegisteredServer.getServerInfo().getName())));
        handledSpecific = true;
      } else if (servers.size() == 1) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-in-queue.other.specific")
            .arguments(Component.text(player.getUsername()), Component.text(s.getServerInfo().getName())));
        handledSpecific = true;
      }

      if (velocityRegisteredServer.getQueueStatus().isQueued(player.getUuid())) {
        amountDone++;
        velocityRegisteredServer.getQueueStatus().dequeue(player.getUuid(), false);
      }
    }

    if (!handledSpecific && amountDone == 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-in-queue.other")
          .arguments(Component.text(player.getUsername())));
      return Command.SINGLE_SUCCESS;
    }

    if (servers.size() > 1) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.remove-all-success")
          .arguments(Component.text(player.getUsername())));
    }
    return Command.SINGLE_SUCCESS;
  }

  private int removeAll(final CommandContext<CommandSource> ctx) {
    if (this.server.getRedisManager().isEnabled()) {
      return this.removeAllRedis(ctx);
    }

    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", true);
    if (server == null) {
      return -1;
    }

    int amount = 0;

    for (Player player : this.server.getAllPlayers()) {
      if (server.getQueueStatus().isQueued(player.getUniqueId())) {
        amount++;
        server.getQueueStatus().dequeue(player.getUniqueId(), false);
      }
    }

    if (amount == 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.removeall-no-players-queued")
          .arguments(Component.text(server.getServerInfo().getName())));
      return -1;
    }

    ctx.getSource()
        .sendMessage(Component.translatable("velocity.queue.command.removedall-player" + (amount == 1 ? "" : "s"))
            .arguments(
                Component.text(amount),
                Component.text(server.getServerInfo().getName())));
    return Command.SINGLE_SUCCESS;
  }

  private int removeAllRedis(final CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = VelocityCommands.getServer(this.server, ctx, "server", true);
    if (server == null) {
      return -1;
    }

    int amount = 0;

    for (RemotePlayerInfo player : this.server.getMultiProxyHandler().getAllPlayers()) {
      if (server.getQueueStatus().isQueued(player.getUuid())) {
        amount++;
        server.getQueueStatus().dequeue(player.getUuid(), false);
      }
    }

    if (amount == 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.removeall-no-players-queued")
          .arguments(Component.text(server.getServerInfo().getName())));
      return -1;
    }

    ctx.getSource()
        .sendMessage(Component.translatable("velocity.queue.command.removedall-player" + (amount == 1 ? "" : "s"))
            .arguments(
                Component.text(amount),
                Component.text(server.getServerInfo().getName())));

    return Command.SINGLE_SUCCESS;
  }
}
