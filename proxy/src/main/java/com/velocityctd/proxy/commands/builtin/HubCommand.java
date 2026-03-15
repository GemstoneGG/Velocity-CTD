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

package com.velocityctd.proxy.commands.builtin;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.command.builtin.BuiltinCommand;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.util.FallbackServerResolver;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;

/**
 * Implements Velocity-CTD's {@code /hub} command.
 */
public class HubCommand implements BuiltinCommand {

  private final VelocityServer server;

  public HubCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "hub";
  }

  @Override
  public BrigadierCommand build() {
    return new BrigadierCommand(BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(source -> source.getPermissionValue("velocity.command.hub") == Tristate.TRUE)
            .executes(this::hub)
            .build()
    );
  }

  private int hub(CommandContext<CommandSource> context) {
    if (!(context.getSource() instanceof ConnectedPlayer player)) {
      context.getSource().sendMessage(CommandMessages.PLAYERS_ONLY);
      return 0;
    }

    VelocityServerConnection con = player.getCurrentServer().orElse(null);
    if (con == null) {
      player.sendMessage(Component.translatable("velocity.command.no-fallbacks"));
      return 0;
    }

    VelocityRegisteredServer currentServer = con.getServer();
    if (currentServer == null) {
      player.sendMessage(Component.translatable("velocity.command.no-fallbacks"));
      return 0;
    }

    List<String> serversToTry = FallbackServerResolver.resolveServersToTry(server, player);
    if (serversToTry.contains(currentServer.getServerInfo().getName())) {
      player.sendMessage(Component.translatable("velocity.command.hub.fallback-already-connected")
              .arguments(Component.text(currentServer.getServerInfo().getName())));
      return 0;
    }

    ConnectedPlayer connectedPlayer = currentServer.getPlayer(player.getUniqueId());
    if (connectedPlayer == null) {
      player.sendMessage(Component.translatable("velocity.command.no-fallbacks"));
      return 0;
    }

    VelocityRegisteredServer nextServer = connectedPlayer.currentServerRetrySession().getNextServerToTry().orElse(null);
    if (nextServer == null) {
      player.sendMessage(Component.translatable("velocity.command.no-fallbacks"));
      return 0;
    }

    if (fallbackConnectingTranslationExists(player)) {
      player.sendMessage(Component.translatable("velocity.command.hub.fallback-connecting")
              .arguments(Component.text(nextServer.getServerInfo().getName())));
    }

    VelocityCommands.sendOrQueue(server, player, nextServer);

    return Command.SINGLE_SUCCESS;
  }

  private static boolean fallbackConnectingTranslationExists(ConnectedPlayer player) {
    Locale locale = player.getEffectiveLocale();

    if (locale == null) {
      locale = Locale.ENGLISH;
    }

    Component format = GlobalTranslator.translator().translate(Component.translatable("velocity.command.hub.fallback-connecting"), locale);
    return format != null && !format.equals(Component.empty());
  }
}
