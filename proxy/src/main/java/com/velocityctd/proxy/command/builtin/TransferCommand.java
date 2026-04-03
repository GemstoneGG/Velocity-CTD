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
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocityctd.proxy.command.PlayerIdentifier;
import com.velocityctd.proxy.util.CompletableUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommand;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import com.velocitypowered.proxy.config.ProxyAddress;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements Velocity-CTD's {@code /transfer} command.
 * Sends players to another proxy if they're above 1.20.5.
 */
public class TransferCommand implements BuiltinCommand {

  private static final Logger LOGGER = LoggerFactory.getLogger(TransferCommand.class);

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
                    .suggests(PlayerIdentifier.suggest(server, "player"))
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

    if (address == null) {
      context.getSource().sendMessage(Component.translatable("velocity.command.error.transfer.invalid-proxy")
              .arguments(Component.text(proxyId)));
      return -1;
    }

    PlayerIdentifier.Result result = PlayerIdentifier.resolve(server, player, context.getSource());
    if (!result.success()) {
      sendResolveError(context.getSource(), result);
      return -1;
    }

    switch (result.type()) {
      case PLAYER -> {
        if (result.players().size() == 1) {
          VelocityClusterPlayer clusterPlayer = result.players().iterator().next();
          clusterPlayer.transfer(address.ip(), address.port()).thenAccept(success -> {
            if (success) {
              context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.player")
                  .arguments(Argument.string("player", clusterPlayer.getUsername()),
                      Argument.string("proxy", normalizedProxyId)));
            } else {
              context.getSource().sendMessage(Component.translatable("velocity.command.transfer.invalid-version")
                  .arguments(Argument.string("player", clusterPlayer.getUsername())));
            }
          }).exceptionally(ex -> {
            handleTransferError(clusterPlayer, ex);
            return null;
          });
        } else {
          // Multiple comma-separated players
          context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.all")
                  .arguments(Component.text(normalizedProxyId)));
          transferAll(result, address);
        }
      }
      case SERVER, CURRENT_SERVER -> {
        context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.server")
                .arguments(
                    Argument.string("server", result.name()),
                    Argument.string("proxy", normalizedProxyId)));
        transferAll(result, address);
      }
      default -> {
        context.getSource().sendMessage(Component.translatable("velocity.command.transfer.success.all")
                .arguments(Component.text(normalizedProxyId)));
        transferAll(result, address);
      }
    }

    return Command.SINGLE_SUCCESS;
  }

  private void transferAll(PlayerIdentifier.Result result, ProxyAddress address) {
    for (VelocityClusterPlayer clusterPlayer : result.players()) {
      clusterPlayer.transfer(address.ip(), address.port()).exceptionally(ex -> {
        handleTransferError(clusterPlayer, ex);
        return null;
      });
    }
  }

  private void handleTransferError(VelocityClusterPlayer player, Throwable ex) {
    if (!(CompletableUtils.cause(ex) instanceof TimeoutException)) {
      LOGGER.error("Failed to transfer player {}", player.getUsername(), ex);
    }
  }

  private void sendResolveError(CommandSource source, PlayerIdentifier.Result result) {
    switch (result.type()) {
      case PLAYER -> source.sendMessage(CommandMessages.PLAYER_NOT_FOUND
          .arguments(Argument.string("player", result.name())));
      case SERVER -> source.sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(result.name())));
      case PLAYER_EXECUTOR_REQUIRED -> source.sendMessage(CommandMessages.PLAYERS_ONLY);
      default -> {
      }
    }
  }

  private String normalizeProxyId(String inputProxyId) {
    return server.getConfiguration().getProxyAddresses().stream()
            .map(ProxyAddress::proxyId)
            .filter(s -> s.equalsIgnoreCase(inputProxyId))
            .findFirst()
            .orElse(inputProxyId);
  }
}
