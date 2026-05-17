/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements Velocity-CTD's {@code /plist} command.
 */
public class PlistCommand implements BuiltinCommandDefinition {

  private static final String SERVER_ARG = "server";
  private static final String SERVER_ALL = "all";

  private static final String PROXY_ARG = "proxy";
  private static final String PROXY_ALL = "all";

  private final VelocityServer server;

  public PlistCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "plist";
  }

  @Override
  public BrigadierCommand build() {
    if (!server.getClusterProxyService().isMultiProxy()) {
      return null;
    }

    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(source ->
                    source.getPermissionValue("velocity.command.plist") == Tristate.TRUE)
            .executes(this::totalCount);
    ArgumentCommandNode<CommandSource, String> serverNode = BrigadierCommand
            .requiredArgumentBuilder(PROXY_ARG, StringArgumentType.string())
            .suggests(CommandUtils.suggestProxy(server, PROXY_ARG, PROXY_ALL))
            .executes(this::proxyCount)
            .then(
                    BrigadierCommand.requiredArgumentBuilder(SERVER_ARG, StringArgumentType.string())
                            .suggests(CommandUtils.suggestServer(server, SERVER_ARG, true, false, SERVER_ALL))
                            .executes(this::serverCount)
                            .build()
            )
            .build();

    rootNode.then(serverNode);
    return new BrigadierCommand(rootNode);
  }

  private int totalCount(CommandContext<CommandSource> context) {
    CommandSource source = context.getSource();
    sendTotalProxyCount(source, null, this.server.getClusterPlayerService().getTotalPlayerCount());

    if (!context.getArguments().containsKey(PROXY_ARG)) {
      source.sendMessage(
          Component.translatable("velocity.command.plist-view-proxy", NamedTextColor.YELLOW)
              .arguments(Argument.string("alias", VelocityCommands.readAlias(context.getNodes()))));
    }

    return SINGLE_SUCCESS;
  }

  private Optional<String> validateProxy(String proxyName, CommandSource source) {
    return server.getClusterProxyService().getAllProxyIds().stream()
            .filter(proxyId -> proxyId.equalsIgnoreCase(proxyName))
            .findFirst()
            .or(() -> {
              source.sendMessage(Component.translatable("velocity.command.proxy-does-not-exist", NamedTextColor.RED)
                      .arguments(Component.text(proxyName)));
              return Optional.empty();
            });
  }

  private int serverCount(CommandContext<CommandSource> context) {
    String proxyName = getString(context, PROXY_ARG);
    String serverName = getString(context, SERVER_ARG);

    String validatedProxy;
    if (proxyName.equalsIgnoreCase(PROXY_ALL)) {
      validatedProxy = null;
    } else {
      validatedProxy = validateProxy(proxyName, context.getSource()).orElse(null);
      if (validatedProxy == null) {
        return 0;
      }
    }

    if (serverName.equalsIgnoreCase(SERVER_ALL)) {
      int totalPlayers = 0;
      for (VelocityRegisteredServer registeredServer : server.getAllServers()) {
        int serverTotalPlayers = sendServerPlayers(context.getSource(), validatedProxy, registeredServer, true);
        totalPlayers += serverTotalPlayers;
      }

      sendTotalProxyCount(context.getSource(), validatedProxy, totalPlayers);
      return SINGLE_SUCCESS;
    }

    VelocityRegisteredServer validatedServer = validateServer(serverName, context.getSource()).orElse(null);
    if (validatedServer == null) {
      return 0;
    }

    sendServerPlayers(context.getSource(), validatedProxy, validatedServer, false);
    return SINGLE_SUCCESS;
  }

  private int proxyCount(CommandContext<CommandSource> context) {
    String proxyName = getString(context, PROXY_ARG);
    if (proxyName.equals(PROXY_ALL)) {
      return totalCount(context);
    }

    Optional<String> validatedProxy = validateProxy(proxyName, context.getSource());
    if (validatedProxy.isEmpty()) {
      return 0;
    }

    Collection<VelocityClusterPlayer> proxyPlayers = server.getClusterPlayerService().getPlayersOnProxy(validatedProxy.get());
    sendTotalProxyCount(context.getSource(), validatedProxy.get(), proxyPlayers.size());
    return SINGLE_SUCCESS;
  }

  private Optional<VelocityRegisteredServer> validateServer(String serverName, CommandSource source) {
    return server.getAllServers().stream()
            .filter(registeredServer -> registeredServer.getServerInfo().getName().equalsIgnoreCase(serverName))
            .findFirst()
            .or(() -> {
              source.sendMessage(Component.translatable("velocity.command.server-does-not-exist", NamedTextColor.RED)
                      .arguments(Component.text(serverName)));
              return Optional.empty();
            });
  }

  private void sendTotalProxyCount(CommandSource target, @Nullable String proxyId, int online) {
    TranslatableComponent.Builder msg;
    if (proxyId != null) {
      msg = Component.translatable()
          .key(online == 1
              ? "velocity.command.plist-player-singular"
              : "velocity.command.plist-player-plural"
          ).color(NamedTextColor.YELLOW)
          .arguments(
              Argument.numeric("count", online),
              Argument.string("proxy", proxyId)
          );
    } else {
      msg = Component.translatable()
          .key(online == 1
              ? "velocity.command.plist-global-player-singular"
              : "velocity.command.plist-global-player-plural"
          ).color(NamedTextColor.YELLOW)
          .arguments(
              Argument.numeric("count", online)
          );
    }

    target.sendMessage(msg.build());
  }

  // Returns total player count
  private int sendServerPlayers(CommandSource target,
                                @Nullable String proxyId,
                                VelocityRegisteredServer server,
                                boolean ignoreEmpty) {
    List<Component> players = new ArrayList<>();
    int totalPlayers = 0;

    for (VelocityClusterPlayer player : this.server.getClusterPlayerService().getPlayersOnServer(server.getServerInfo().getName())) {
      if (proxyId == null || player.getProxyId().equalsIgnoreCase(proxyId)) {
        players.add(Component.text(player.getUsername()));
        totalPlayers++;
      }
    }

    if (ignoreEmpty && totalPlayers == 0) {
      return 0;
    }

    Component playerList = players.stream()
            .reduce((a, b) -> a.append(Component.text(", ")).append(b))
            .orElse(Component.text(""));
    target.sendMessage(Component.translatable("velocity.command.plist-server")
            .arguments(
                    Argument.string("server", server.getServerInfo().getName()),
                    Argument.numeric("count", totalPlayers),
                    Argument.component("players", playerList)
            )
    );

    return totalPlayers;
  }
}
