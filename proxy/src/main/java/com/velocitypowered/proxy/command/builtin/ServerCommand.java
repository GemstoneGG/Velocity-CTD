/*
 * Copyright (C) 2018-2023 Velocity Contributors
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

import static net.kyori.adventure.text.event.HoverEvent.showText;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implements Velocity's {@code /server} command.
 */
public final class ServerCommand {
  private static final String SERVER_ARG = "server";
  public static final int MAX_SERVERS_TO_LIST = 50;

  private final VelocityServer server;

  public ServerCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(final boolean isServerEnabled) {
    if (!isServerEnabled) {
      return;
    }

    final LiteralCommandNode<CommandSource> node = BrigadierCommand
        .literalArgumentBuilder("server")
        .requires(src -> src instanceof Player && src.getPermissionValue("velocity.command.server") != Tristate.FALSE)
        .executes(ctx -> {
          if (server.getConfiguration().isOverrideServerCommandUsage()) {
            return VelocityCommands.emitUsage(ctx, "server");
          }

          final Player player = (Player) ctx.getSource();
          outputServerInformation(player, server);
          return Command.SINGLE_SUCCESS;
        })
        .then(BrigadierCommand.requiredArgumentBuilder(SERVER_ARG, StringArgumentType.word())
            .suggests(VelocityCommands.suggestServer(server, SERVER_ARG, true))
            .executes(ctx -> {
              final Player player = (Player) ctx.getSource();
              final VelocityRegisteredServer registeredServer = VelocityCommands.getServer(this.server, ctx, SERVER_ARG, true);

              if (registeredServer == null) {
                return -1;
              }

              ServerConnection connection = player.getCurrentServer().orElse(null);
              if (connection != null && connection.getServerInfo().getName()
                      .equalsIgnoreCase(registeredServer.getServerInfo().getName())) {
                player.sendMessage(Component.translatable("velocity.error.already-connected"));
                return -1;
              }

              if (this.server.getConfiguration().getQueue().getNoQueueServers().contains(registeredServer.getServerInfo().getName())
                      || !server.getQueueManager().isQueueEnabled()
                      || player.hasPermission("velocity.queue.bypass")) {
                player.createConnectionRequest(registeredServer).connectWithIndication();
                return Command.SINGLE_SUCCESS;
              }

              server.getQueueManager().queue(player, registeredServer);
              return Command.SINGLE_SUCCESS;
            })
        ).build();

    final BrigadierCommand command = new BrigadierCommand(node);
    String[] aliases = server.getConfiguration().getServerAliases().toArray(new String[0]);

    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .aliases(aliases)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private static void outputServerInformation(final Player executor,
                                              final ProxyServer server) {
    final String currentServer = executor.getCurrentServer()
        .map(ServerConnection::getServerInfo)
        .map(ServerInfo::getName)
        .orElse("<unknown>");
    executor.sendMessage(Component.translatable(
        "velocity.command.server-current-server",
        NamedTextColor.YELLOW,
        Component.text(currentServer)));

    final List<RegisteredServer> servers = VelocityCommands.sortedServerList(server);
    if (servers.size() > MAX_SERVERS_TO_LIST) {
      executor.sendMessage(Component.translatable(
          "velocity.command.server-too-many", NamedTextColor.RED));
      return;
    }

    // Filter servers based on player permissions
    final List<RegisteredServer> accessibleServers = servers.stream()
        .filter(rs -> executor.getPermissionValue("velocity.command.server."
            + rs.getServerInfo().getName()) != Tristate.FALSE)
        .toList();

    if (accessibleServers.isEmpty()) {
      // No accessible servers, return without showing the list
      return;
    }

    // Assemble the list of servers as components
    final TextComponent.Builder serverListBuilder = Component.text()
        .append(Component.translatable("velocity.command.server-available",
            NamedTextColor.YELLOW))
        .appendSpace();
    for (int i = 0; i < accessibleServers.size(); i++) {
      final RegisteredServer rs = accessibleServers.get(i);
      serverListBuilder.append(formatServerComponent(currentServer, rs));
      if (i != accessibleServers.size() - 1) {
        serverListBuilder.append(Component.text(", ", NamedTextColor.GRAY));
      }
    }

    executor.sendMessage(serverListBuilder.build());
  }

  private static TextComponent formatServerComponent(final String currentPlayerServer,
                                                     final RegisteredServer server) {
    final ServerInfo serverInfo = server.getServerInfo();
    final TextComponent.Builder serverTextComponent = Component.text()
            .content(serverInfo.getName());

    final int connectedPlayers = server.getPlayersConnected().size();
    final TranslatableComponent.Builder playersTextComponent = Component.translatable();
    if (connectedPlayers == 1) {
      playersTextComponent.key("velocity.command.server-tooltip-player-online");
    } else {
      playersTextComponent.key("velocity.command.server-tooltip-players-online");
    }
    playersTextComponent.arguments(Component.text(connectedPlayers));
    if (serverInfo.getName().equals(currentPlayerServer)) {
      serverTextComponent.color(NamedTextColor.GREEN)
          .hoverEvent(
              showText(
                  Component.empty()
                      .append(Component.translatable("velocity.command.server-tooltip-current-server"))
                      .append(Component.newline())
                      .append(playersTextComponent))
          );
    } else {
      serverTextComponent.color(NamedTextColor.GRAY)
          .clickEvent(ClickEvent.runCommand("/server " + serverInfo.getName()))
          .hoverEvent(
              showText(
                  Component.empty()
                      .append(Component.translatable("velocity.command.server-tooltip-offer-connect-server"))
                      .append(Component.newline())
                      .append(playersTextComponent))
          );
    }
    return serverTextComponent.build();
  }
}
