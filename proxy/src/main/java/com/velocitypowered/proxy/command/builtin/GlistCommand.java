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

import static com.mojang.brigadier.arguments.StringArgumentType.getString;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity's {@code /glist} command.
 */
public class GlistCommand implements BuiltinCommand {

  private static final String SERVER_ARG = "server";
  private static final String SERVER_ALL = "all";

  private final VelocityServer server;

  public GlistCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "glist";
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(source ->
                    source.getPermissionValue("velocity.command.glist") == Tristate.TRUE)
            .executes(this::totalCount);
    ArgumentCommandNode<CommandSource, String> serverNode = BrigadierCommand
            .requiredArgumentBuilder(SERVER_ARG, StringArgumentType.string())
            .suggests(CommandUtils.suggestServer(server, SERVER_ARG, true, false, SERVER_ALL))
            .executes(this::serverCount)
            .build();

    rootNode.then(serverNode);
    return new BrigadierCommand(rootNode);
  }

  private int totalCount(CommandContext<CommandSource> context) {
    CommandSource source = context.getSource();
    sendTotalProxyCount(source);
    source.sendMessage(
            Component.translatable("velocity.command.glist-view-all", NamedTextColor.YELLOW)
                    .arguments(Argument.string("alias", VelocityCommands.readAlias(context.getNodes()))));

    return 1;
  }

  private int serverCount(CommandContext<CommandSource> context) {
    CommandSource source = context.getSource();
    String serverName = getString(context, SERVER_ARG);
    if (serverName.equalsIgnoreCase(SERVER_ALL)) {
      for (VelocityRegisteredServer server : CommandUtils.sortedServerList(server)) {
        sendServerPlayers(source, true, server);
      }
      sendTotalProxyCount(source);
    } else {
      Optional<VelocityRegisteredServer> registeredServer = server.getServer(serverName);
      if (registeredServer.isEmpty()) {
        source.sendMessage(
                CommandMessages.SERVER_DOES_NOT_EXIST
                        .arguments(Component.text(serverName)));
        return -1;
      }
      sendServerPlayers(source, false, registeredServer.get());
    }

    return Command.SINGLE_SUCCESS;
  }

  private void sendTotalProxyCount(CommandSource target) {
    int online = server.getClusterPlayerService().getTotalPlayerCount();

    String msgKey = (online == 1)
        ? "velocity.command.glist-player-singular"
        : "velocity.command.glist-player-plural";

    if (server.getClusterProxyService().isMultiProxy()) {
      msgKey += "-proxy-plural";
    }

    TranslatableComponent.Builder msg = Component.translatable()
        .key(msgKey)
        .color(NamedTextColor.YELLOW)
        .arguments(
            Argument.string("players", Integer.toString(online))
        );

    target.sendMessage(msg.build());
  }

  private void sendServerPlayers(CommandSource target, boolean fromAll, VelocityRegisteredServer server) {
    int totalPlayers = 0;
    List<Component> players = new ArrayList<>();
    String selfProxyId = this.server.getClusterProxyService().getSelfProxyId();

    for (VelocityClusterPlayer player : this.server.getClusterPlayerService().getPlayersOnServer(server.getServerInfo().getName())) {
      String proxyId = player.getProxyId();
      String key = "velocity.command.glist.proxy-" + (proxyId.equals(selfProxyId) ? "self" : "other");
      Component hover = Component.translatable(key).arguments(Component.text(proxyId));
      players.add(Component.text(player.getUsername()).hoverEvent(HoverEvent.showText(hover)));
      totalPlayers += 1;
    }

    if (totalPlayers == 0 && fromAll) {
      return;
    }

    Component playerList = players.stream()
            .reduce((a, b) -> a.append(Component.text(", ")).append(b))
            .orElse(Component.text(""));
    target.sendMessage(Component.translatable("velocity.command.glist-server")
            .arguments(
                    Argument.string("server", server.getServerInfo().getName()),
                    Argument.numeric("count", totalPlayers),
                    Argument.component("players", playerList)
            )
    );
  }
}
