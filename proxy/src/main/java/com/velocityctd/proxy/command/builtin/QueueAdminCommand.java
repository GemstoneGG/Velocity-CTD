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
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity-CTD's {@code /queueadmin} command.
 */
public class QueueAdminCommand implements BuiltinCommandDefinition {

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
    String label = label();
    LiteralCommandNode<CommandSource> listQueues = BrigadierCommand.literalArgumentBuilder("listqueues")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.listqueues") == Tristate.TRUE)
            .executes(this::listQueues)
            .build();
    LiteralCommandNode<CommandSource> list = BrigadierCommand.literalArgumentBuilder("list")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.list") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.queueadmin.list.usage"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "server", false, false, "all", "current"))
                    .executes(this::list))
            .build();
    LiteralCommandNode<CommandSource> pause = BrigadierCommand.literalArgumentBuilder("pause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.pause") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.queueadmin.pause.usage"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "server", false, false))
                    .executes(this::pause))
            .build();
    LiteralCommandNode<CommandSource> unpause = BrigadierCommand.literalArgumentBuilder("unpause")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.unpause") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.queueadmin.unpause.usage"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "server", false, false))
                    .executes(this::unpause))
            .build();
    LiteralCommandNode<CommandSource> add = BrigadierCommand.literalArgumentBuilder("add")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.add") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.queueadmin.add.usage"))
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandUtils.suggestPlayer(server, ctx, builder))
                    .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.queueadmin.add.usage"))
                    .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                            .suggests(CommandUtils.suggestServer(server, "server", false, false))
                            .executes(this::add)))
            .build();
    LiteralCommandNode<CommandSource> addall = BrigadierCommand.literalArgumentBuilder("addall")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.addall") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.queueadmin.addall.usage"))
            .then(BrigadierCommand.requiredArgumentBuilder("from", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "from", true, false))
                    .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.queueadmin.addall.usage"))
                    .then(BrigadierCommand.requiredArgumentBuilder("to", StringArgumentType.word())
                            .suggests(CommandUtils.suggestServer(server, "to", false, false))
                            .executes(this::addAll)))
            .build();
    LiteralCommandNode<CommandSource> remove = BrigadierCommand.literalArgumentBuilder("remove")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.remove") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.queueadmin.remove.usage"))
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandUtils.suggestPlayer(server, ctx, builder))
                    .executes(this::remove)
                    .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                            .suggests(CommandUtils.suggestServer(server, "server", false, false))
                            .executes(this::remove)))
            .build();
    LiteralCommandNode<CommandSource> removeall = BrigadierCommand.literalArgumentBuilder("removeall")
            .requires(source -> source.getPermissionValue("velocity.queue.admin.removeall") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, "velocity.command.queueadmin.removeall.usage"))
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                    .suggests(CommandUtils.suggestServer(server, "server", false, false))
                    .executes(this::removeAll))
            .build();
    List<LiteralCommandNode<CommandSource>> commands = List
            .of(listQueues, list, pause, unpause, add, addall, remove, removeall);

    return new BrigadierCommand(
            commands.stream()
                    .reduce(
                            BrigadierCommand.literalArgumentBuilder(label)
                                    .executes(ctx -> {
                                      CommandSource source = ctx.getSource();
                                      String availableCommands = commands.stream()
                                              .filter(e -> e.getRequirement().test(source))
                                              .map(LiteralCommandNode::getName)
                                              .collect(Collectors.joining("|"));
                                      String commandText = "/%s <%s>".formatted(label, availableCommands);
                                      source.sendMessage(Component.text(commandText, NamedTextColor.RED));
                                      return 0;
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

    for (VelocityQueue<?> queue : listedQueues()) {
      source.sendMessage(createListComponent(queue));
    }

    return SINGLE_SUCCESS;
  }

  /**
   * Returns every queue suitable for listing, including the shared queues of groups.
   *
   * @return the listable queues
   */
  private List<VelocityQueue<?>> listedQueues() {
    List<VelocityQueue<?>> queues = new ArrayList<>();
    for (VelocityQueue<?> queue : this.server.getQueueManager().getQueues()) {
      if (queue.getState() != QueueState.INACTIVE) {
        queues.add(queue);
      }
    }
    return queues;
  }

  private Component createListComponent(VelocityQueue<?> queue) {
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

    if (serverName.equalsIgnoreCase("all")) {
      Set<UUID> uniquePlayers = new HashSet<>();
      List<VelocityQueue<?>> queues = listedQueues();
      for (VelocityQueue<?> queue : queues) {
        for (VelocityQueueEntry queueEntry : queue.getEntries()) {
          uniquePlayers.add(queueEntry.getUniqueId());
        }
      }

      int uniquePlayerCount = uniquePlayers.size();
      String key = uniquePlayerCount == 1
          ? "velocity.queue.command.list.global-singular"
          : "velocity.queue.command.list.global-plural";
      ctx.getSource().sendMessage(Component.translatable(key)
          .arguments(Argument.numeric("count", uniquePlayerCount)));

      for (VelocityQueue<?> queue : queues) {
        sendQueueList(ctx.getSource(), queue.getName(), queue.getEntries(), false);
      }
      return SINGLE_SUCCESS;
    }

    VelocityQueue<?> queue;
    String displayName = serverName;
    if (serverName.equalsIgnoreCase("current")) {
      if (!(ctx.getSource() instanceof ConnectedPlayer player)) {
        ctx.getSource().sendMessage(Component.translatable("velocity.command.players-only"));
        return 0;
      }

      VelocityServerConnection connection = player.getCurrentServer().orElse(null);
      if (connection == null) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.current-not-connected"));
        return 0;
      }

      displayName = connection.getServerInfo().getName();
      queue = this.server.getQueueManager().getQueue(displayName);
    } else {
      queue = CommandUtils.resolveQueue(this.server, ctx, "server", false, false);
      if (queue == null) {
        return 0;
      }
      displayName = queue.getName();
    }

    if (queue.getState() == QueueState.INACTIVE) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.list.disabled-queue")
          .arguments(Component.text(displayName)));
      return 0;
    }

    sendQueueList(ctx.getSource(), displayName, queue.getEntries(), true);
    return SINGLE_SUCCESS;
  }

  /**
   * Sends the queued players of one destination as a list row.
   *
   * @param source       the command source to message
   * @param name         the destination name shown to the source
   * @param entries      the queued players
   * @param includeEmpty whether an empty destination should still produce a message
   */
  private void sendQueueList(CommandSource source, String name,
                             Collection<? extends VelocityQueueEntry> entries, boolean includeEmpty) {
    List<Component> players = new ArrayList<>();
    for (VelocityQueueEntry queueEntry : entries) {
      players.add(Component.text(queueEntry.getUsername()));
    }

    if (players.isEmpty()) {
      if (includeEmpty) {
        source.sendMessage(Component.translatable("velocity.queue.command.list.empty")
            .arguments(Component.text(name)));
      }
      return;
    }

    Component playerList = players.stream()
        .reduce((a, b) -> a.append(Component.text(", ")).append(b))
        .orElse(Component.text(""));
    source.sendMessage(Component.translatable("velocity.queue.command.list.server")
        .arguments(
            Argument.string("server", name),
            Argument.numeric("count", players.size()),
            Argument.component("players", playerList)));
  }

  private int pause(CommandContext<CommandSource> ctx) {
    VelocityQueue<?> queue = CommandUtils.resolveQueue(this.server, ctx, "server", false);
    if (queue == null) {
      return 0;
    }

    String serverName = queue.getName();
    if (queue.getState() == QueueState.PAUSED) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-paused")
              .arguments(Component.text(serverName)));
      return 0;
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
    VelocityQueue<?> queue = CommandUtils.resolveQueue(this.server, ctx, "server", false);
    if (queue == null) {
      return 0;
    }

    String serverName = queue.getName();
    if (queue.getState() != QueueState.PAUSED) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-paused")
              .arguments(Component.text(serverName)));
      return 0;
    }

    queue.setState(QueueState.ACTIVE);

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.unpause")
            .arguments(Component.text(serverName)));

    queue.broadcastMessage(
        q -> Component.translatable("velocity.queue.command.unpaused")
            .arguments(Component.text(serverName))
    );
    return SINGLE_SUCCESS;
  }

  private int add(CommandContext<CommandSource> ctx) {
    String playerName = ctx.getArgument("player", String.class);
    VelocityQueue<?> queue = CommandUtils.resolveQueue(this.server, ctx, "server", false);

    if (queue == null) {
      return 0;
    }

    String destinationName = queue.getName();

    Optional<VelocityClusterPlayer> maybePlayer = this.server.getClusterPlayerService().getPlayer(playerName);
    if (maybePlayer.isEmpty()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
              .arguments(Argument.string("player", playerName)));
      return 0;
    }

    VelocityClusterPlayer player = maybePlayer.get();

    if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
      for (VelocityQueue<?> existingQueue : this.server.getQueueManager().getQueues()) {
        if (existingQueue.contains(player.getUniqueId())) {
          ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
                  .arguments(
                          Argument.string("player", player.getUsername()),
                          Argument.string("server", existingQueue.getName())));
          return 0;
        }
      }
    }

    String conn = player.getServerName();
    List<String> groupMembers = CommandUtils.getGroupBackendNames(this.server, destinationName);
    boolean onMember = groupMembers != null
        && groupMembers.stream().anyMatch(member -> member.equalsIgnoreCase(conn));
    if (conn != null && (conn.equalsIgnoreCase(destinationName) || onMember)) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-connected")
              .arguments(Argument.string("player", player.getUsername())));
      return 0;
    }

    if (queue.contains(player.getUniqueId())) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.already-queued.other")
              .arguments(
                      Argument.string("player", player.getUsername()),
                      Argument.string("server", destinationName)));
      return 0;
    }

    queue.enqueue(player.toQueueEntryData(destinationName));

    ctx.getSource().sendMessage(Component.translatable("velocity.queue.command.added")
            .arguments(
                    Argument.string("player", player.getUsername()),
                    Argument.string("server", destinationName)));

    return SINGLE_SUCCESS;
  }

  private int addAll(CommandContext<CommandSource> ctx) {
    VelocityQueue<?> fromQueue = CommandUtils.resolveQueue(this.server, ctx, "from", true);
    if (fromQueue == null) {
      return 0;
    }

    VelocityQueue<?> toQueue = CommandUtils.resolveQueue(this.server, ctx, "to", false);
    if (toQueue == null) {
      return 0;
    }

    String fromName = fromQueue.getName();
    String toName = toQueue.getName();
    if (toName.equalsIgnoreCase(fromName)) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.same-server-queue"));
      return 0;
    }

    List<String> fromBackends = CommandUtils.getGroupBackendNames(this.server, ctx
        .getArgument("from", String.class));
    if (fromBackends == null) {
      fromBackends = List.of(ctx.getArgument("from", String.class));
    }

    List<VelocityClusterPlayer> eligible = new ArrayList<>();
    Set<UUID> seen = new HashSet<>();
    for (VelocityClusterPlayer player : this.server.getClusterPlayerService().getAllPlayers()) {
      String conn = player.getServerName();
      if (conn == null || fromBackends.stream().noneMatch(conn::equalsIgnoreCase)) {
        continue;
      }
      if (!seen.add(player.getUniqueId())) {
        continue;
      }
      if (!this.server.getConfiguration().getQueue().isAllowMultiQueue()) {
        boolean alreadyQueued = this.server.getQueueManager().getQueues().stream()
                .anyMatch(queue -> queue.contains(player.getUniqueId()));
        if (alreadyQueued) {
          continue;
        }
      }
      if (!toQueue.contains(player.getUniqueId())) {
        eligible.add(player);
      }
    }

    if (eligible.isEmpty()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.addall-no-players-queued", NamedTextColor.RED)
              .arguments(
                      Argument.string("from", fromName),
                      Argument.string("to", toName)));
      return 0;
    }
    for (VelocityClusterPlayer player : eligible) {
      toQueue.enqueue(player.toQueueEntryData(toName));
    }

    String key = eligible.size() == 1
        ? "velocity.queue.command.addedall-player"
        : "velocity.queue.command.addedall-players";
    ctx.getSource().sendMessage(Component.translatable(key)
            .arguments(
                    Argument.numeric("count", eligible.size()),
                    Argument.string("server", toName)));

    return eligible.size();
  }

  private int remove(CommandContext<CommandSource> ctx) {
    String playerName = ctx.getArgument("player", String.class);

    Optional<VelocityClusterPlayer> maybePlayer = this.server.getClusterPlayerService().getPlayer(playerName);
    if (maybePlayer.isEmpty()) {
      ctx.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
              .arguments(Argument.string("player", playerName)));
      return 0;
    }

    VelocityClusterPlayer player = maybePlayer.get();

    List<VelocityQueue<?>> queues;
    if (ctx.getArguments().containsKey("server")) {
      VelocityQueue<?> queue = CommandUtils.resolveQueue(this.server, ctx, "server", false);
      if (queue == null) {
        return 0;
      }
      queues = List.of(queue);
    } else {
      queues = new ArrayList<>(this.server.getQueueManager().getQueues());
    }

    boolean handledSpecific = false;
    int amountDone = 0;

    for (VelocityQueue<?> queue : queues) {
      String name = queue.getName();
      if (queues.size() == 1 && queue.contains(player.getUniqueId())) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.remove-success")
                .arguments(Argument.string("player", player.getUsername()),
                        Argument.string("server", name)));
        handledSpecific = true;
      } else if (queues.size() == 1) {
        ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-in-queue.other.specific")
                .arguments(Argument.string("player", player.getUsername()),
                        Argument.string("server", name)));
        handledSpecific = true;
      }

      if (queue.contains(player.getUniqueId())) {
        amountDone++;
        queue.dequeue(player.getUniqueId());
      }
    }

    if (!handledSpecific && amountDone == 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.not-in-queue.other")
              .arguments(Argument.string("player", player.getUsername())));
      return 0;
    } else {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.remove-all-success")
          .arguments(Argument.string("player", player.getUsername())));
      return amountDone;
    }
  }

  private int removeAll(CommandContext<CommandSource> ctx) {
    VelocityQueue<?> queue = CommandUtils.resolveQueue(this.server, ctx, "server", true);
    if (queue == null) {
      return 0;
    }

    String name = queue.getName();
    int amount = 0;

    for (VelocityClusterPlayer player : this.server.getClusterPlayerService().getAllPlayers()) {
      if (queue.contains(player.getUniqueId())) {
        amount++;
        queue.dequeue(player.getUniqueId());
      }
    }

    if (amount == 0) {
      ctx.getSource().sendMessage(Component.translatable("velocity.queue.error.removeall-no-players-queued")
              .arguments(Component.text(name)));
      return 0;
    }

    String key = amount == 1
        ? "velocity.queue.command.removedall-player"
        : "velocity.queue.command.removedall-players";
    ctx.getSource().sendMessage(Component.translatable(key)
            .arguments(
                    Argument.numeric("count", amount),
                    Argument.string("server", name)));
    return amount;
  }
}
