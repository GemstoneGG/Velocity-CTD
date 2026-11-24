/*
 * Copyright (C) 2020-2023 Velocity Contributors
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
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity's {@code /glist} command.
 */
public class GlistCommand implements BuiltinCommandDefinition {

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

    return SINGLE_SUCCESS;
  }

  private int serverCount(CommandContext<CommandSource> context) {
    CommandSource source = context.getSource();
    String serverName = getString(context, SERVER_ARG);
    if (serverName.equalsIgnoreCase(SERVER_ALL)) {
      Set<UUID> seen = new HashSet<>();
      for (VelocityRegisteredServer registeredServer : CommandUtils.sortedServerList(server)) {
        String backendName = registeredServer.getServerInfo().getName();
        sendPlayerList(source, backendName, collectPlayers(List.of(backendName), seen), true);
      }
      sendTotalProxyCount(source);
      return SINGLE_SUCCESS;
    }

    List<String> groupBackends = CommandUtils.getGroupBackendNames(server, serverName);
    if (groupBackends != null) {
      sendPlayerList(source, serverName, collectPlayers(groupBackends, new HashSet<>()), false);
      return SINGLE_SUCCESS;
    }

    Optional<VelocityRegisteredServer> registeredServer = server.getServer(serverName);
    if (registeredServer.isEmpty()) {
      source.sendMessage(
              CommandMessages.SERVER_DOES_NOT_EXIST
                      .arguments(Component.text(serverName)));
      return 0;
    }

    String backendName = registeredServer.get().getServerInfo().getName();
    sendPlayerList(source, backendName, collectPlayers(List.of(backendName), new HashSet<>()), false);
    return SINGLE_SUCCESS;
  }

  /**
   * Collects online cluster players from the given backends, keeping one entry per player.
   *
   * @param backendNames the backends to inspect
   * @param seen         player ids already shown in an earlier row
   * @return the distinct players in first-seen order
   */
  private List<VelocityClusterPlayer> collectPlayers(Collection<String> backendNames, Set<UUID> seen) {
    Map<UUID, VelocityClusterPlayer> found = new LinkedHashMap<>();
    for (String backendName : backendNames) {
      for (VelocityClusterPlayer player : this.server.getClusterPlayerService().getPlayersOnServer(backendName)) {
        if (seen.add(player.getUniqueId())) {
          found.put(player.getUniqueId(), player);
        }
      }
    }
    return List.copyOf(found.values());
  }

  private void sendTotalProxyCount(CommandSource target) {
    int online = server.getClusterPlayerService().getTotalPlayerCount();

    boolean multiProxy = server.getClusterProxyService().isMultiProxy();
    String msgKey = (online == 1)
        ? (multiProxy ? "velocity.command.glist-player-singular-proxy-plural" : "velocity.command.glist-player-singular")
        : (multiProxy ? "velocity.command.glist-player-plural-proxy-plural" : "velocity.command.glist-player-plural");

    TranslatableComponent.Builder msg = Component.translatable()
        .key(msgKey)
        .color(NamedTextColor.YELLOW)
        .arguments(
            Argument.string("players", Integer.toString(online))
        );

    target.sendMessage(msg.build());
  }

  /**
   * Renders one {@code /glist} row for a server or group.
   *
   * @param target   the command source to message
   * @param name     the row's destination label
   * @param players  the players to list
   * @param fromAll  whether empty rows should be suppressed
   */
  private void sendPlayerList(CommandSource target, String name,
                              Collection<VelocityClusterPlayer> players, boolean fromAll) {
    List<Component> playerComponents = new ArrayList<>();
    String selfProxyId = this.server.getClusterProxyService().getSelfProxyId();

    for (VelocityClusterPlayer player : players) {
      String proxyId = player.getProxyId();
      String key = proxyId.equals(selfProxyId)
          ? "velocity.command.glist.proxy-self"
          : "velocity.command.glist.proxy-other";
      Component hover = Component.translatable(key).arguments(Component.text(proxyId));
      playerComponents.add(Component.text(player.getUsername()).hoverEvent(HoverEvent.showText(hover)));
    }

    if (playerComponents.isEmpty() && fromAll) {
      return;
    }

    Component playerList = playerComponents.stream()
            .reduce((a, b) -> a.append(Component.text(", ")).append(b))
            .orElse(Component.text(""));
    target.sendMessage(Component.translatable("velocity.command.glist-server")
            .arguments(
                    Argument.string("server", name),
                    Argument.numeric("count", playerComponents.size()),
                    Argument.component("players", playerList)
            )
    );
  }
}
