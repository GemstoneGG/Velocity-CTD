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

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocityctd.proxy.cluster.ClusterPlayer;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommand;
import com.velocitypowered.proxy.config.ProxyAddress;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Implements Velocity-CTD's {@code /transfer} command.
 * Sends players to another proxy if they're above 1.20.5.
 */
public class TransferCommand implements BuiltinCommand {

  private final VelocityServer server;

  public TransferCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "transfer";
  }

  @Override
  public BrigadierCommand build() {
    if (!this.server.getConfiguration().isAcceptTransfers()) {
      return null;
    }

    LiteralCommandNode<CommandSource> transfer = BrigadierCommand.literalArgumentBuilder(label())
            .requires(source -> source.getPermissionValue("velocity.command.transfer") == Tristate.TRUE)
            .executes(ctx -> CommandUtils.emitUsage(ctx, label()))
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                      String argument = ctx.getArguments().containsKey("player")
                              ? ctx.getArgument("player", String.class)
                              : "";

                      if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
                        builder.suggest("all");
                      }

                      if ("current".regionMatches(true, 0, argument, 0, argument.length())
                              && ctx.getSource() instanceof ConnectedPlayer) {
                        builder.suggest("current");
                      }

                      if (argument.isEmpty() || argument.startsWith("+")) {
                        for (VelocityRegisteredServer server : server.getAllServers()) {
                          String serverName = server.getServerInfo().getName();

                          if (serverName.regionMatches(true, 0, argument, 1, argument.length() - 1)) {
                            builder.suggest("+" + serverName);
                          }
                        }
                      }

                      for (String playerName : server.getClusterPlayerService().getPlayerNames()) {
                        if (playerName.regionMatches(true, 0, argument, 0, argument.length())) {
                          builder.suggest(playerName);
                        }
                      }

                      return builder.buildFuture();
                    })
                    .executes(ctx -> CommandUtils.emitUsage(ctx, label()))
                    .then(BrigadierCommand.requiredArgumentBuilder("proxy-id", StringArgumentType.word())
                            .suggests(CommandUtils.suggestProxy(server, "proxy-id"))
                            .executes(this::transfer)))
            .build();

    return new BrigadierCommand(transfer);
  }

  private int transfer(CommandContext<CommandSource> context) {
    String player = context.getArgument("player", String.class);
    String proxyId = context.getArgument("proxy-id", String.class);
    String normalizedProxyId = normalizeProxyId(proxyId);

    ProxyAddress address = server.getConfiguration().getProxyAddresses().stream()
            .filter(proxy -> proxy.proxyId().equalsIgnoreCase(proxyId))
            .findFirst()
            .orElse(null);

    if (!player.equalsIgnoreCase("all") && !player.equalsIgnoreCase("current")
            && !player.startsWith("+")
            && !this.server.getClusterPlayerService().isPlayerOnline(player)) {
      context.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
              .arguments(Argument.string("player", player)));
      return -1;
    }

    if (address == null) {
      context.getSource().sendMessage(Component.translatable("velocity.command.error.transfer.invalid-proxy")
              .arguments(Component.text(proxyId)));
      return -1;
    }

    if (player.equalsIgnoreCase("all")) {
      context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.all")
              .arguments(Component.text(normalizedProxyId)));

      this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        for (ConnectedPlayer connectedPlayer : this.server.getAllPlayers()) {
          if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            connectedPlayer.transferToHost(new InetSocketAddress(address.ip(), address.port()));
          }
        }
      }).delay(1, TimeUnit.SECONDS).schedule();
    } else if (player.startsWith("+")) {
      VelocityRegisteredServer foundServer = findServer(player.substring(1)).orElse(null);
      if (foundServer == null) {
        context.getSource().sendMessage(Component.translatable("velocity.command.server-does-not-exist")
                .arguments(Component.text(player)));
        return -1;
      }

      context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.server")
              .arguments(
                      Argument.string("server", foundServer.getServerInfo().getName()),
                      Argument.string("proxy", normalizedProxyId)));

      this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        for (ConnectedPlayer connectedPlayer : foundServer.getPlayersConnected()) {
          if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            connectedPlayer.transferToHost(new InetSocketAddress(address.ip(), address.port()));
          }
        }
      }).delay(1, TimeUnit.SECONDS).schedule();
    } else if (player.equalsIgnoreCase("current")) {
      if (!(context.getSource() instanceof ConnectedPlayer sender)) {
        context.getSource().sendMessage(Component.translatable("velocity.command.players-only"));
        return -1;
      }

      VelocityServerConnection foundServerConn = sender.getCurrentServer().orElse(null);
      if (foundServerConn == null) {
        context.getSource().sendMessage(Component.translatable("velocity.command.server-does-not-exist")
                .arguments(Component.text(player)));
        return -1;
      }

      VelocityRegisteredServer foundServer = this.server.getServer(foundServerConn.getServerInfo().getName()).orElseThrow();

      context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.server")
              .arguments(Component.text(foundServer.getServerInfo().getName()),
                      Argument.string("proxy", normalizedProxyId)));

      this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        for (ConnectedPlayer connectedPlayer : foundServer.getPlayersConnected()) {
          if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            connectedPlayer.transferToHost(new InetSocketAddress(address.ip(), address.port()));
          }
        }
      }).delay(1, TimeUnit.SECONDS).schedule();
    } else {
      Optional<ClusterPlayer> maybeClusterPlayer = this.server.getClusterPlayerService().getPlayer(player);
      if (maybeClusterPlayer.isEmpty()) {
        context.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
                .arguments(Argument.string("player", player)));
        return -1;
      }

      ClusterPlayer clusterPlayer = maybeClusterPlayer.get();
      context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.player")
              .arguments(Argument.string("player", clusterPlayer.getUsername()),
                      Argument.string("proxy", normalizedProxyId)));

      clusterPlayer.transfer(context.getSource(), address.ip(), address.port());
    }

    return Command.SINGLE_SUCCESS;
  }

  private Optional<VelocityRegisteredServer> findServer(String serverName) {
    Collection<VelocityRegisteredServer> servers = server.getAllServers();
    String lowerServerName = serverName.toLowerCase();

    Optional<VelocityRegisteredServer> bestMatch = Optional.empty();

    for (VelocityRegisteredServer server : servers) {
      String lowerName = server.getServerInfo().getName().toLowerCase();

      if (lowerName.equals(lowerServerName)) {
        bestMatch = Optional.of(server);
        break;
      }

      if (lowerName.contains(lowerServerName)) {
        if (bestMatch.isPresent()) {
          break;
        }

        bestMatch = Optional.of(server);
      }
    }

    return bestMatch;
  }

  private String normalizeProxyId(String inputProxyId) {
    return server.getConfiguration().getProxyAddresses().stream()
            .map(ProxyAddress::proxyId)
            .filter(s -> s.equalsIgnoreCase(inputProxyId))
            .findFirst()
            .orElse(inputProxyId);
  }
}
