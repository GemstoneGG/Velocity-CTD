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

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static net.kyori.adventure.text.event.HoverEvent.showText;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity's {@code /server} command.
 */
public class ServerCommand implements BuiltinCommandDefinition {

  private static final String SERVER_ARG = "server";

  public static final int MAX_SERVERS_TO_LIST = 50;

  private final VelocityServer server;

  public ServerCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "server";
  }

  @Override
  public List<String> aliases() {
    if (server.isQueueEnabled()) {
      return server.getConfiguration().getServerAliases();
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public BrigadierCommand build() {
    LiteralCommandNode<CommandSource> node = BrigadierCommand
        .literalArgumentBuilder(label())
        .requires(src -> src instanceof ConnectedPlayer && src.getPermissionValue("velocity.command.server") != Tristate.FALSE)
        .executes(ctx -> {
          if (server.getConfiguration().isOverrideServerCommandUsage()) {
            return CommandUtils.emitUsage(ctx, "velocity.command.server.usage");
          }

          ConnectedPlayer player = (ConnectedPlayer) ctx.getSource();
          outputServerInformation(player, server);
          return SINGLE_SUCCESS;
        })
        .then(BrigadierCommand.requiredArgumentBuilder(SERVER_ARG, StringArgumentType.word())
            .suggests(CommandUtils.suggestServer(server, SERVER_ARG, true, true))
            .executes(ctx -> {
              ConnectedPlayer player = (ConnectedPlayer) ctx.getSource();
              VelocityRegisteredServer registeredServer = CommandUtils.getServer(server, ctx, SERVER_ARG, true);

              if (registeredServer == null) {
                return 0;
              }

              VelocityServerConnection connection = player.getCurrentServer().orElse(null);
              if (connection != null && connection.getServer() == registeredServer) {
                player.sendMessage(Component.translatable("velocity.error.already-connected"));
                return 0;
              }

              CommandUtils.sendOrQueue(server, player, registeredServer);
              return SINGLE_SUCCESS;
            })
        ).build();

    return new BrigadierCommand(node);
  }

  private static void outputServerInformation(ConnectedPlayer executor, VelocityServer server) {
    String currentServer = executor.getCurrentServer()
        .map(VelocityServerConnection::getServerInfo)
        .map(ServerInfo::getName)
        .orElse("<unknown>");
    executor.sendMessage(Component.translatable(
        "velocity.command.server-current-server", NamedTextColor.YELLOW)
            .arguments(Component.text(currentServer)));

    List<VelocityRegisteredServer> servers = CommandUtils.sortedServerList(server);
    if (servers.size() > MAX_SERVERS_TO_LIST) {
      executor.sendMessage(Component.translatable(
          "velocity.command.server-too-many", NamedTextColor.RED));
      return;
    }

    // Filter servers based on player permissions
    List<VelocityRegisteredServer> accessibleServers = servers.stream()
        .filter(rs -> executor.getPermissionValue("velocity.command.server."
            + rs.getServerInfo().getName()) != Tristate.FALSE)
        .toList();

    if (accessibleServers.isEmpty()) {
      // No accessible servers, return without showing the list
      return;
    }

    // Assemble the list of servers as components
    TextComponent.Builder serverListBuilder = Component.text()
        .append(Component.translatable("velocity.command.server-available",
            NamedTextColor.YELLOW))
        .appendSpace();
    for (int i = 0; i < accessibleServers.size(); i++) {
      VelocityRegisteredServer rs = accessibleServers.get(i);
      serverListBuilder.append(formatServerComponent(currentServer, rs));
      if (i != accessibleServers.size() - 1) {
        serverListBuilder.append(Component.text(", ", NamedTextColor.GRAY));
      }
    }

    executor.sendMessage(serverListBuilder.build());
  }

  private static TextComponent formatServerComponent(String currentPlayerServer, VelocityRegisteredServer server) {
    ServerInfo serverInfo = server.getServerInfo();
    TextComponent.Builder serverTextComponent = Component.text()
            .content(serverInfo.getName());

    int connectedPlayers = server.getPlayersConnected().size();
    TranslatableComponent.Builder playersTextComponent = Component.translatable();
    if (connectedPlayers == 1) {
      playersTextComponent.key("velocity.command.server-tooltip-player-online");
    } else {
      playersTextComponent.key("velocity.command.server-tooltip-players-online");
    }
    playersTextComponent.arguments(Argument.numeric("players", connectedPlayers));
    if (serverInfo.getName().equals(currentPlayerServer)) {
      serverTextComponent.color(NamedTextColor.GREEN)
          .hoverEvent(
              showText(
                  Component.translatable("velocity.command.server-tooltip-current-server")
                      .append(Component.newline())
                      .append(playersTextComponent))
          );
    } else {
      serverTextComponent.color(NamedTextColor.GRAY)
          .clickEvent(ClickEvent.runCommand("/server " + serverInfo.getName()))
          .hoverEvent(
              showText(
                  Component.translatable("velocity.command.server-tooltip-offer-connect-server")
                      .append(Component.newline())
                      .append(playersTextComponent))
          );
    }

    return serverTextComponent.build();
  }
}
