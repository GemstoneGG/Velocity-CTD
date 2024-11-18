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
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.config.ProxyAddress;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler;
import com.velocitypowered.proxy.redis.multiproxy.RedisPlayerSetTransferringRequest;
import com.velocitypowered.proxy.redis.multiproxy.RedisTransferCommandRequest;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Command that sends players to another proxy, if they're above 1.20.5.
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

                      builder.suggest("all");

                      if (server.getMultiProxyHandler().isEnabled()) {
                        for (MultiProxyHandler.RemotePlayerInfo info : server.getMultiProxyHandler().getAllPlayers()) {
                          if (info.name.regionMatches(true, 0, argument, 0, argument.length())) {
                            builder.suggest(info.name);
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
                            .suggests((ctx, builder) -> VelocityCommands.suggestProxy(server, ctx, builder))
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

    if (!this.server.getMultiProxyHandler().getAllProxyIds().contains(proxyId)) {
      context.getSource().sendMessage(Component.translatable("velocity.command.error.transfer.invalid-proxy")
          .arguments(Component.text(proxyId)));
      return -1;
    }

    if (!this.server.getMultiProxyHandler().isPlayerOnline(player) && !player.equalsIgnoreCase("all")) {
      context.getSource().sendMessage(Component.translatable("velocity.command.error.transfer.invalid-player")
              .arguments(Component.text(player)));
      return -1;
    }

    ProxyAddress address = this.server.getConfiguration().getProxyAddresses().stream()
            .filter(proxy -> proxy.proxyId().equalsIgnoreCase(proxyId)).findFirst().orElse(null);

    if (address == null) {
      context.getSource().sendMessage(Component.translatable("velocity.command.error.transfer.invalid-proxy")
              .arguments(Component.text(proxyId)));
      return -1;
    }


    if (player.equalsIgnoreCase("all")) {
      context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.all")
              .arguments(Component.text(proxyId)));
      for (Player p : this.server.getAllPlayers()) {
        ConnectedPlayer connectedPlayer = (ConnectedPlayer) p;

        if (p.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
          String connectedServer = connectedPlayer.getConnectedServer() != null
                  ? connectedPlayer.getConnectedServer().getServerInfo().getName() : null;
          this.server.getRedisManager().send(new RedisPlayerSetTransferringRequest(connectedPlayer.getUniqueId(), true,
                        connectedServer));
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
    } else {
      context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.player")
              .arguments(Component.text(player), Component.text(proxyId)));
      this.server.getRedisManager().send(new RedisTransferCommandRequest(player, proxyId, address.ip(), address.port()));
    }

    return Command.SINGLE_SUCCESS;
  }

  private int usage(final CommandContext<CommandSource> context) {
    context.getSource().sendMessage(
        Component.translatable("velocity.command.transfer.usage", NamedTextColor.YELLOW)
    );
    return Command.SINGLE_SUCCESS;
  }
}
