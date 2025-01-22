/*
 * Copyright (C) 2018-2024 Velocity Contributors
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
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.config.ProxyAddress;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.redis.multiproxy.RedisPlayerSetTransferringRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisTransferCommandRequest;
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Command that sends players to another proxy if they're above 1.20.5.
 */
public class TransferCommand {

  private final VelocityServer server;

  /**
   * Constructs the /transfer command.
   *
   * @param server The proxy.
   */
  public TransferCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers the command.
   *
   * @param isTransferEnabled Whether to enable it or not.
   */
  public void register(final boolean isTransferEnabled) {
    if (!isTransferEnabled) {
      return;
    }

    if (!this.server.getConfiguration().isAcceptTransfers()) {
      return;
    }

    final LiteralCommandNode<CommandSource> transfer = BrigadierCommand.literalArgumentBuilder("transfer")
            .requires(source -> source.getPermissionValue("velocity.command.transfer") == Tristate.TRUE)
            .executes(ctx -> VelocityCommands.emitUsage(ctx, "transfer"))
            .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                      final String argument = ctx.getArguments().containsKey("player")
                              ? ctx.getArgument("player", String.class)
                              : "";

                      if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
                        builder.suggest("all");
                      }
                      if ("current".regionMatches(true, 0, argument, 0, argument.length())
                          && ctx.getSource() instanceof Player) {
                        builder.suggest("current");
                      }

                      if (argument.isEmpty() || argument.startsWith("+")) {
                        for (final RegisteredServer server : server.getAllServers()) {
                          final String serverName = server.getServerInfo().getName();

                          if (serverName.regionMatches(true, 0, argument, 1, argument.length() - 1)) {
                            builder.suggest("+" + serverName);
                          }
                        }
                      }

                      if (server.getMultiProxyHandler().isRedisEnabled()) {
                        for (RemotePlayerInfo info : server.getMultiProxyHandler().getAllPlayers()) {
                          if (info.getName().regionMatches(true, 0, argument, 0, argument.length())) {
                            builder.suggest(info.getName());
                          }
                        }

                        return builder.buildFuture();
                      }

                      for (final Player player : server.getAllPlayers()) {
                        final String playerName = player.getUsername();
                        if (playerName.regionMatches(true, 0, argument, 0, argument.length())) {
                          builder.suggest(playerName);
                        }
                      }

                      return builder.buildFuture();
                    })
                    .executes(ctx -> VelocityCommands.emitUsage(ctx, "transfer"))
                    .then(BrigadierCommand.requiredArgumentBuilder("proxy-id", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                              final String argument = ctx.getArguments().containsKey("proxy")
                                  ? ctx.getArgument("proxy", String.class)
                                  : "";
                              for (ProxyAddress address : server.getConfiguration().getProxyAddresses()) {
                                if (address.proxyId().regionMatches(true, 0, argument, 0, argument.length())) {
                                  builder.suggest(address.proxyId());
                                }
                              }
                              return builder.buildFuture();
                            })
                            .executes(this::transfer)))
            .build();

    final BrigadierCommand command = new BrigadierCommand(transfer);
    server.getCommandManager().register(
        server.getCommandManager().metaBuilder(command)
            .plugin(VelocityVirtualPlugin.INSTANCE)
            .build(),
        command
    );
  }

  private int transfer(final CommandContext<CommandSource> context) {
    final String player = context.getArgument("player", String.class);
    final String proxyId = context.getArgument("proxy-id", String.class);
    final String normalizedProxyId = normalizeProxyId(proxyId);

    ProxyAddress address = server.getConfiguration().getProxyAddresses().stream()
        .filter(proxy -> proxy.proxyId().equalsIgnoreCase(proxyId))
        .findFirst()
        .orElse(null);

    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      if (this.server.getMultiProxyHandler().isPlayerOnline(player) && !player.equalsIgnoreCase("all")
          && !player.equalsIgnoreCase("current") && !player.startsWith("+")) {
        context.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
            .arguments(Component.text(player)));
        return -1;
      }
    } else {
      if (this.server.getPlayer(player).isEmpty() && !player.equalsIgnoreCase("all") && !player.equalsIgnoreCase("current")
          && !player.startsWith("+")) {
        context.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
            .arguments(Component.text(player)));
        return -1;
      }
    }

    if (address == null) {
      context.getSource().sendMessage(Component.translatable("velocity.command.error.transfer.invalid-proxy")
          .arguments(Component.text(proxyId)));
      return -1;
    }

    if (player.equalsIgnoreCase("all")) {
      context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.all")
          .arguments(Component.text(normalizedProxyId)));

      if (this.server.getMultiProxyHandler().isRedisEnabled()) {
        for (Player p : this.server.getAllPlayers()) {
          ConnectedPlayer connectedPlayer = (ConnectedPlayer) p;

          if (p.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            String connectedServer = connectedPlayer.getConnectedServer() != null
                ? connectedPlayer.getConnectedServer().getServerInfo().getName() : null;
            this.server.getRedisManager().send(new RedisPlayerSetTransferringRequest(connectedPlayer.getUniqueId(), true,
                connectedServer));
          }
        }
      }

      this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        for (Player p : this.server.getAllPlayers()) {
          ConnectedPlayer connectedPlayer = (ConnectedPlayer) p;
          if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            connectedPlayer.transferToHost(new InetSocketAddress(address.ip(), address.port()));
          }
        }
      }).delay(1, TimeUnit.SECONDS).schedule();
    } else if (player.startsWith("+")) {
      RegisteredServer foundServer = findServer(player.substring(1)).orElse(null);
      if (foundServer == null) {
        context.getSource().sendMessage(Component.translatable("velocity.command.server-does-not-exist")
            .arguments(Component.text(player)));
        return -1;
      }

      context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.server")
          .arguments(Component.text(foundServer.getServerInfo().getName()), Component.text(normalizedProxyId)));

      if (this.server.getMultiProxyHandler().isRedisEnabled()) {
        for (Player p : foundServer.getPlayersConnected()) {
          ConnectedPlayer connectedPlayer = (ConnectedPlayer) p;

          if (p.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            String connectedServer = connectedPlayer.getConnectedServer() != null
                ? connectedPlayer.getConnectedServer().getServerInfo().getName() : null;
            this.server.getRedisManager().send(new RedisPlayerSetTransferringRequest(connectedPlayer.getUniqueId(), true,
                connectedServer));
          }
        }
      }

      this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        for (Player p : foundServer.getPlayersConnected()) {
          ConnectedPlayer connectedPlayer = (ConnectedPlayer) p;
          if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            connectedPlayer.transferToHost(new InetSocketAddress(address.ip(), address.port()));
          }
        }
      }).delay(1, TimeUnit.SECONDS).schedule();

    } else if (player.equalsIgnoreCase("current")) {
      if (!(context.getSource() instanceof Player sender)) {
        context.getSource().sendMessage(Component.translatable("velocity.command.players-only"));
        return -1;
      }

      ServerConnection foundServerConn = sender.getCurrentServer().orElse(null);
      if (foundServerConn == null) {
        context.getSource().sendMessage(Component.translatable("velocity.command.server-does-not-exist")
            .arguments(Component.text(player)));
        return -1;
      }

      RegisteredServer foundServer = this.server.getServer(foundServerConn.getServerInfo().getName()).orElse(null);

      context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.server")
          .arguments(Component.text(foundServer.getServerInfo().getName()), Component.text(normalizedProxyId)));

      if (this.server.getMultiProxyHandler().isRedisEnabled()) {
        for (Player p : foundServer.getPlayersConnected()) {
          ConnectedPlayer connectedPlayer = (ConnectedPlayer) p;

          if (p.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            String connectedServer = connectedPlayer.getConnectedServer() != null
                ? connectedPlayer.getConnectedServer().getServerInfo().getName() : null;
            this.server.getRedisManager().send(new RedisPlayerSetTransferringRequest(connectedPlayer.getUniqueId(), true,
                connectedServer));
          }
        }
      }

      this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        for (Player p : foundServer.getPlayersConnected()) {
          ConnectedPlayer connectedPlayer = (ConnectedPlayer) p;
          if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            connectedPlayer.transferToHost(new InetSocketAddress(address.ip(), address.port()));
          }
        }
      }).delay(1, TimeUnit.SECONDS).schedule();
    } else {
      if (this.server.getMultiProxyHandler().isRedisEnabled()) {
        UUID sender = null;
        if (context.getSource() instanceof Player p) {
          sender = p.getUniqueId();
        }

        RemotePlayerInfo playerInfo = this.server.getMultiProxyHandler().getPlayerInfo(player);

        if (playerInfo == null || playerInfo.getName() == null || playerInfo.getProxyId() == null) {
          context.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
              .arguments(Component.text(player)));
          return -1;
        }

        context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.player")
            .arguments(Component.text(playerInfo.getName()), Component.text(normalizedProxyId)));

        this.server.getRedisManager().send(new RedisTransferCommandRequest(
            sender, playerInfo.getName(), proxyId, address.ip(), address.port()
        ));
      } else {
        Optional<Player> maybePlayer = this.server.getPlayer(player);
        if (maybePlayer.isEmpty()) {
          context.getSource().sendMessage(Component.translatable("velocity.command.player-not-found")
              .arguments(Component.text(player)));
          return -1;
        }

        ConnectedPlayer connectedPlayer = (ConnectedPlayer) maybePlayer.get();
        if (connectedPlayer.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
          context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.player")
              .arguments(Component.text(connectedPlayer.getUsername()), Component.text(normalizedProxyId)));
          connectedPlayer.transferToHost(new InetSocketAddress(address.ip(), address.port()));
        } else {
          context.getSource().sendMessage(Component.translatable("velocity.command.transfer.invalid-version")
              .arguments(Component.text(connectedPlayer.getUsername())));
        }
      }
    }

    return Command.SINGLE_SUCCESS;
  }

  private int usage(final CommandContext<CommandSource> context) {
    context.getSource().sendMessage(
        Component.translatable("velocity.command.transfer.usage", NamedTextColor.YELLOW)
    );
    return Command.SINGLE_SUCCESS;
  }

  private Optional<RegisteredServer> findServer(final String serverName) {
    final Collection<RegisteredServer> servers = server.getAllServers();
    final String lowerServerName = serverName.toLowerCase();

    Optional<RegisteredServer> bestMatch = Optional.empty();

    for (RegisteredServer server : servers) {
      final String lowerName = server.getServerInfo().getName().toLowerCase();

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

  private String normalizeProxyId(final String inputProxyId) {
    return server.getConfiguration().getProxyAddresses().stream()
        .map(ProxyAddress::proxyId)
        .filter(s -> s.equalsIgnoreCase(inputProxyId))
        .findFirst()
        .orElse(inputProxyId);
  }
}
