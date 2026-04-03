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

package com.velocityctd.proxy.command.builtin;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.velocityctd.api.queue.QueueState.PAUSED;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocityctd.proxy.queue.VelocityQueueEntry;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommand;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity-CTD's {@code /queueadmin} command.
 */
public class QueueAdminCommand implements BuiltinCommand {

  private final VelocityServer server;

  public QueueAdminCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return server.getConfiguration().getQueue().getQueueAdminAliases()
            .stream()
            .findFirst()
            .orElse("queueadmin");
  }

  @Override
  public List<String> aliases() {
    return server.getConfiguration().getQueue().getQueueAdminAliases()
            .stream()
            .skip(1) // label()
            .toList();
  }

  @Override
  public BrigadierCommand build() {
    LiteralCommandNode<CommandSource> listQueues = BrigadierCommand.literalArgumentBuilder("listqueues")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.listqueues") == Tristate.TRUE)
            .executes(this::listQueues)
            .build();
    LiteralCommandNode<CommandSource> list = BrigadierCommand.literalArgumentBuilder("list")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.list") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "queueadmin.list"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                      String argument = ctx.getArguments().containsKey("server")
                              ? ctx.getArgument("server", String.class)
                              : "";

                      if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
                        builder.suggest("all");
                      }

                      if ("current".regionMatches(true, 0, argument, 0, argument.length())) {
                        builder.suggest("current");
                      }

                      for (VelocityRegisteredServer s : server.getAllServers()) {
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
    LiteralCommandNode<CommandSource> pause = BrigadierCommand.literalArgumentBuilder("pause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.pause") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "queueadmin.pause"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "server", false, false))
                    .executes(this::pause))
            .build();
    LiteralCommandNode<CommandSource> unpause = BrigadierCommand.literalArgumentBuilder("unpause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.unpause") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "queueadmin.unpause"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "server", false, false))
                    .executes(this::unpause))
            .build();
    LiteralCommandNode<CommandSource> add = BrigadierCommand.literalArgumentBuilder("add")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.add") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "queueadmin.add"))
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandUtils.suggestPlayer(server, ctx, builder))
                    .executes(ctx -> CommandUtils.emitUsage(ctx, "queueadmin.add"))
                    .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                            .suggests(CommandUtils.suggestServer(server, "server", false, false))
                            .executes(this::add)))
            .build();
    LiteralCommandNode<CommandSource> addall = BrigadierCommand.literalArgumentBuilder("addall")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.addall") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "queueadmin.addall"))
            .then(BrigadierCommand.requiredArgumentBuilder("from", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "from", true, false))
                    .executes(ctx -> CommandUtils.emitUsage(ctx, "queueadmin.addall"))
                    .then(BrigadierCommand.requiredArgumentBuilder("to", StringArgumentType.word())
                            .suggests(CommandUtils.suggestServer(server, "to", false, false))
                            .executes(this::addAll)))
            .build();
    LiteralCommandNode<CommandSource> remove = BrigadierCommand.literalArgumentBuilder("remove")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.remove") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "queueadmin.remove"))
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandUtils.suggestPlayer(server, ctx, builder))
                    .executes(this::remove)
                    .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                            .suggests(CommandUtils.suggestServer(server, "server", false, false))
                            .executes(this::remove)))
            .build();
    LiteralCommandNode<CommandSource> removeall = BrigadierCommand.literalArgumentBuilder("removeall")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.removeall") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "queueadmin.removeall"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "server", false, false))
                    .executes(this::removeAll))
            .build();
    List<LiteralCommandNode<CommandSource>> commands = List
            .of(listQueues, list, pause, unpause, add, addall, remove, removeall);

    return new BrigadierCommand(
            commands.stream()
                    .reduce(
                            BrigadierCommand.literalArgumentBuilder("queueadmin")
                                    .executes(ctx -> {
                                      CommandSource source = ctx.getSource();
                                      String availableCommands = commands.stream()
                                              .filter(e -> e.getRequirement().test(source))
                                              .map(LiteralCommandNode::getName)
                                              .collect(Collectors.joining("|"));
                                      String commandText = "/queueadmin <%s>".formatted(availableCommands);
                                      source.sendMessage(Component.text(commandText, NamedTextColor.RED));
                                      return SINGLE_SUCCESS;
                                    })
                                    .requires(commands.stream()
                                            .map(CommandNode::getRequirement)
                                            .reduce(Predicate::or)
                                            .orElseThrow()),
                            ArgumentBuilder::then,
                            ArgumentBuilder::then));
  }

  private int listQueues(CommandContext<CommandSource> ctx) {
    CommandSource source = ctx.getSource();
    source.sendMessage(Component.translatable("velocity.queue.command.listqueues.header"));

    for (VelocityRegisteredServer server : this.server.getAllServers()) {
      if (this.server.getConfiguration().getQueue().getNoQueueServers().contains(server.getServerInfo().getName())) {
        continue;
      }

      VelocityQueue queue = this.server.getQueueManager().getQueue(server.getServerInfo().getName());

      source.sendMessage(createListComponent(queue));
    }

    return SINGLE_SUCCESS;
  }

  private Component createListComponent(VelocityQueue queue) {
    return Component.translatable("velocity.queue.command.listqueues.item")
        .arguments(
            Argument.component("server",
                Component.text(queue.getName())
                    .hoverEvent(
                        Component.translatable("velocity.queue.command.listqueues.hover")
                            .arguments(
                                Argument.numeric("size", queue.size()),
                                Argument.string("paused", queue.getState() == PAUSED ? "True" : "False"),
                                Argument.string("online", queue.getServerStatus().isActive() ? "True" : "False")
                            ).asHoverEvent()
                    )
            )
        );
  }

  private int list(CommandContext<CommandSource> ctx) {
    String serverName = ctx.getArgument("server", String.class);
    VelocityRegisteredServer server = this.server.getServer(ctx
            .getArgument("server", String.class)).orElse(null);

    if (serverName.equalsIgnoreCase("current")) {
      if (!(ctx.getSource() instanceof ConnectedPlayer p)) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.players-only"));
        return SINGLE_SUCCESS;
      }

      VelocityServerConnection connection = p.getCurrentServer().orElse(null);
      if (connection == null) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.current-not-connected"));
        return SINGLE_SUCCESS;
      }

      server = this.server.getServer(connection.getServerInfo().getName()).orElse(null);
    }

    if (serverName.equalsIgnoreCase("all")) {
      Set<UUID> uniquePlayers = new HashSet<>();

      for (VelocityQueue queue : this.server.getQueueManager().getQueues()) {
        for (VelocityQueueEntry queueEntry : queue.getEntries()) {
          uniquePlayers.add(queueEntry.getUniqueId());
        }
      }

      int uniquePlayerCount = uniquePlayers.size();

      if (uniquePlayerCount == 1) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.global-singular")
                .arguments(Argument.numeric("count", uniquePlayerCount)));
      } else {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.global-plural")
                .arguments(Argument.numeric("count", uniquePlayerCount)));
      }

      for (VelocityRegisteredServer s : this.server.getAllServers()) {
        if (this.server.getConfiguration().getQueue().getNoQueueServers().contains(s.getServerInfo().getName())) {
          continue;
        }

        List<Component> players = new ArrayList<>();

        for (VelocityQueueEntry queueEntry : s.getQueue().getEntries()) {
          players.add(Component.text(queueEntry.getUsername()));
        }

        players.stream()
                .reduce((a, b) -> a.append(Component.text(", ")).append(b))
                .ifPresent(playerList -> {
                  TranslatableComponent.Builder builder = Component.translatable()
                          .key("velocity.queue.command.list.server")
                          .arguments(
                                  Argument.string("server", s.getServerInfo().getName()),
                                  Argument.numeric("count", players.size()),
                                  Argument.component("players", playerList));
                  ctx.getSource().sendMessage(builder.build());
                });
      }
      return SINGLE_SUCCESS;
    } else if (server == null) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.server-does-not-exist")
              .arguments(Component.text(serverName)));
      return SINGLE_SUCCESS;
    }

    List<Component> players = new ArrayList<>();

    if (this.server.getConfiguration().getQueue().getNoQueueServers().contains(server.getServerInfo().getName())) {
      String newName = serverName;
      if (serverName.equalsIgnoreCase("current") && ctx.getSource() instanceof ConnectedPlayer cp) {
        VelocityServerConnection conn = cp.getCurrentServer().orElse(null);
        if (conn != null) {
          newName = conn.getServerInfo().getName();
        }
      }

      ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.disabled-queue")
              .arguments(Component.text(newName)));
      return SINGLE_SUCCESS;
    }

    for (VelocityQueueEntry queueEntry : server.getQueue().getEntries()) {
      players.add(Component.text(queueEntry.getUsername()));
    }

    VelocityRegisteredServer finalServer = server;
    players.stream()
            .reduce((a, b) -> a.append(Component.text(", ")).append(b))
            .ifPresentOrElse(playerList -> {
              TranslatableComponent.Builder builder = Component.translatable()
                      .key("velocity.queue.command.list.server")
                      .arguments(
                              Argument.string("server", finalServer.getServerInfo().getName()),
                              Argument.numeric("count", players.size()),
                              Argument.component("players", playerList));
              ctx.getSource().sendMessage(builder.build());
            }, () -> {
              if (players.isEmpty()) {
                ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.empty")
                        .arguments(Component.text(finalServer.getServerInfo().getName())));
              }
            });

    return SINGLE_SUCCESS;
  }

  private int pause(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = CommandUtils.getServer(this.server, ctx, "server", false);
    if (server == null) {
      return SINGLE_SUCCESS;
    }

    VelocityQueue queue = server.getQueue();
    String serverName = server.getServerInfo().getName();
    if (queue.getState() == QueueState.PAUSED) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-paused")
              .arguments(Component.text(serverName)));
      return SINGLE_SUCCESS;
    }

    queue.setState(QueueState.PAUSED);

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.pause")
            .arguments(Component.text(serverName)));

    queue.broadcastMessage(
        q -> Component.translatable("velocity.queue.command.paused")
            .arguments(Component.text(serverName))
    );

    return SINGLE_SUCCESS;
  }

  private int unpause(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = CommandUtils.getServer(this.server, ctx, "server", false);
    if (server == null) {
      return SINGLE_SUCCESS;
    }

    VelocityQueue queue = server.getQueue();
    if (queue.getState() != QueueState.PAUSED) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-paused")
              .arguments(Component.text(server.getServerInfo().getName())));
      return SINGLE_SUCCESS;
    }

    queue.setState(QueueState.ACTIVE);

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.unpause")
            .arguments(Component.text(server.getServerInfo().getName())));

    queue.broadcastMessage(
        q -> Component.translatable("velocity.queue.command.unpaused")
            .arguments(Component.text(server.getServerInfo().getName()))
    );
    return SINGLE_SUCCESS;
  }

  private int add(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = CommandUtils.getServer(this.server, ctx, "server", false);
    String playerName = ctx.getArgument("player", String.class);

    if (server == null) {
      return SINGLE_SUCCESS;
    }

    Optional<VelocityClusterPlayer> maybePlayer = this.server.getClusterPlayerService().getPlayer(playerName);
    if (maybePlayer.isEmpty()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
              .arguments(Argument.string("player", playerName)));
      return SINGLE_SUCCESS;
    }

    VelocityClusterPlayer player = maybePlayer.get();

    if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
      for (VelocityQueue queue : this.server.getQueueManager().getQueues()) {
        if (queue.contains(player.getUniqueId())) {
          ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
                  .arguments(
                          Argument.string("player", player.getUsername()),
                          Argument.string("server", queue.getName())));
          return SINGLE_SUCCESS;
        }
      }
    }

    String conn = player.getServerName();
    if (conn != null && conn.equalsIgnoreCase(server.getServerInfo().getName())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-connected")
              .arguments(Argument.string("player", player.getUsername())));
      return SINGLE_SUCCESS;
    }

    if (server.getQueue().contains(player.getUniqueId())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
              .arguments(
                      Argument.string("player", player.getUsername()),
                      Argument.string("server", server.getServerInfo().getName())));
      return SINGLE_SUCCESS;
    }

    server.getQueue().enqueue(player.toQueueEntryData(server.getServerInfo().getName()));

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.added")
            .arguments(
                    Argument.string("player", player.getUsername()),
                    Argument.string("server", server.getServerInfo().getName())));

    return SINGLE_SUCCESS;
  }

  private int addAll(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer from = CommandUtils.getServer(this.server, ctx, "from", true);
    if (from == null) {
      return SINGLE_SUCCESS;
    }

    VelocityRegisteredServer to = CommandUtils.getServer(this.server, ctx, "to", false);
    if (to == null) {
      return SINGLE_SUCCESS;
    }

    if (to.getServerInfo().getName().equalsIgnoreCase(from.getServerInfo().getName())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.same-server-queue"));
      return SINGLE_SUCCESS;
    }

    String fromName = from.getServerInfo().getName();
    String toName = to.getServerInfo().getName();

    List<VelocityClusterPlayer> eligible = new ArrayList<>();
    for (VelocityClusterPlayer player : this.server.getClusterPlayerService().getAllPlayers()) {
      String conn = player.getServerName();
      if (conn != null && conn.equalsIgnoreCase(fromName)) {
        if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
          boolean alreadyQueued = this.server.getQueueManager().getQueues().stream()
                  .anyMatch(queue -> queue.contains(player.getUniqueId()));
          if (alreadyQueued) {
            continue;
          }
        }
        if (!to.getQueue().contains(player.getUniqueId())) {
          eligible.add(player);
        }
      }
    }

    if (eligible.isEmpty()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.addall-no-players-queued", NamedTextColor.RED)
              .arguments(
                      Argument.string("from", fromName),
                      Argument.string("to", toName)));
      return SINGLE_SUCCESS;
    }
    for (VelocityClusterPlayer player : eligible) {
      to.getQueue().enqueue(player.toQueueEntryData(toName));
    }

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.addedall-player" + (eligible.size() == 1 ? "" : "s"))
            .arguments(
                    Argument.numeric("count", eligible.size()),
                    Argument.string("server", toName)));

    return SINGLE_SUCCESS;
  }

  private int remove(CommandContext<CommandSource> ctx) {
    String playerName = ctx.getArgument("player", String.class);

    Optional<VelocityClusterPlayer> maybePlayer = this.server.getClusterPlayerService().getPlayer(playerName);
    if (maybePlayer.isEmpty()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
              .arguments(Argument.string("player", playerName)));
      return SINGLE_SUCCESS;
    }

    VelocityClusterPlayer player = maybePlayer.get();

    List<VelocityRegisteredServer> servers;
    if (ctx.getArguments().containsKey("server")) {
      VelocityRegisteredServer registeredServer = CommandUtils.getServer(server, ctx, "server", false);
      if (registeredServer == null) {
        return SINGLE_SUCCESS;
      }
      servers = List.of(registeredServer);
    } else {
      servers = new ArrayList<>(this.server.getAllServers());
    }

    boolean handledSpecific = false;
    int amountDone = 0;

    for (VelocityRegisteredServer server : servers) {
      if (servers.size() == 1 && server.getQueue().contains(player.getUniqueId())) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.remove-success")
                .arguments(Argument.string("player", player.getUsername()),
                        Argument.string("server", server.getServerInfo().getName())));
        handledSpecific = true;
      } else if (servers.size() == 1) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-in-queue.other.specific")
                .arguments(Argument.string("player", player.getUsername()),
                        Argument.string("server", server.getServerInfo().getName())));
        handledSpecific = true;
      }

      if (server.getQueue().contains(player.getUniqueId())) {
        amountDone++;
        server.getQueue().dequeue(player.getUniqueId());
      }
    }

    if (!handledSpecific && amountDone == 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-in-queue.other")
              .arguments(Argument.string("player", player.getUsername())));
      return SINGLE_SUCCESS;
    }

    if (servers.size() > 1) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.remove-all-success")
              .arguments(Argument.string("player", player.getUsername())));
    }

    return SINGLE_SUCCESS;
  }

  private int removeAll(CommandContext<CommandSource> ctx) {
    VelocityRegisteredServer server = CommandUtils.getServer(this.server, ctx, "server", true);
    if (server == null) {
      return SINGLE_SUCCESS;
    }

    int amount = 0;

    for (VelocityClusterPlayer player : this.server.getClusterPlayerService().getAllPlayers()) {
      if (server.getQueue().contains(player.getUniqueId())) {
        amount++;
        server.getQueue().dequeue(player.getUniqueId());
      }
    }

    if (amount == 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.removeall-no-players-queued")
              .arguments(Component.text(server.getServerInfo().getName())));
      return SINGLE_SUCCESS;
    }

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.removedall-player" + (amount == 1 ? "" : "s"))
            .arguments(
                    Argument.numeric("count", amount),
                    Argument.string("server", server.getServerInfo().getName())));
    return SINGLE_SUCCESS;
  }

}
