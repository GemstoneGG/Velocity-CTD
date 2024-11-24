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
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;

/**
 * Implements Velocity's {@code /hub} command.
 */
public class HubCommand {

  private final VelocityServer server;

  public HubCommand(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public BrigadierCommand register(final boolean isHubCommand) {
    if (!isHubCommand) {
      return null;
    }

    return new BrigadierCommand(BrigadierCommand
        .literalArgumentBuilder("hub")
        .requires(source ->
          source.getPermissionValue("velocity.command.hub") == Tristate.TRUE)
        .executes(this::lobby).build());
  }

  private int lobby(final CommandContext<CommandSource> context) {
    if (!(context.getSource() instanceof Player player)) {
      context.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
      return 0;
    }

    ServerConnection connection = player.getCurrentServer().orElse(null);
    if (connection == null || connection.getServer() == null) {
      return 0;
    }

    final RegisteredServer registeredServer = connection.getServer();
    if (registeredServer == null) {
      return 0;
    }

    if (server.getConfiguration().getAttemptConnectionOrder().contains(registeredServer.getServerInfo().getName())) {
      player.sendMessage(Component.translatable("velocity.command.hub.fallback-already-connected")
          .arguments(Component.text(registeredServer.getServerInfo().getName())));
      return 0;
    }

    if (registeredServer instanceof VelocityRegisteredServer velocityRegisteredServer) {
      ConnectedPlayer p = velocityRegisteredServer.getPlayer(player.getUniqueId());
      if (p == null) {
        return 0;
      }

      RegisteredServer serverToTry = p.getNextServerToTry().orElse(null);
      if (serverToTry == null) {
        return 0;
      }

      if (translationExists("velocity.command.hub.fallback-connecting", player)) {
        player.sendMessage(Component.translatable("velocity.command.hub.fallback-connecting")
            .arguments(Component.text(serverToTry.getServerInfo().getName())));
      }

      if (this.server.getConfiguration().getQueue().getNoQueueServers()
              .contains(serverToTry.getServerInfo().getName()) || !server.getMultiProxyHandler().isEnabled()
              || player.hasPermission("velocity.queue.bypass")) {
        player.createConnectionRequest(serverToTry).connectWithIndication();
        return Command.SINGLE_SUCCESS;
      }

      ((VelocityRegisteredServer) serverToTry).getQueueStatus().queue(player.getUniqueId(),
          player.getQueuePriority(serverToTry.getServerInfo().getName()),
          player.hasPermission("velocity.queue.full.bypass"));

      return Command.SINGLE_SUCCESS;
    }
    return 0;
  }

  private static boolean translationExists(final String key, final Player player) {
    Locale locale = player.getEffectiveLocale();

    if (locale == null) {
      locale = Locale.ENGLISH;
    }

    Component format = GlobalTranslator.translator().translate(Component.translatable(key), locale);
    return format != null && !format.equals(Component.empty());
  }
}
