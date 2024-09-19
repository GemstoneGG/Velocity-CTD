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

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

/**
 * Implements the Velocity default {@code /glist} command.
 */
public class PlistCommand {

  private final ProxyServer server;

  public PlistCommand(ProxyServer server) {
    this.server = server;
  }

  /**
   * Registers or unregisters the command based on the configuration value.
   */
  public void register(boolean isPlistEnabled) {
    if (!isPlistEnabled) {
      return;
    }

    final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder("plist")
        .requires(source ->
            source.getPermissionValue("velocity.command.plist") == Tristate.TRUE)
        .executes(commandContext -> {
          System.out.println(commandContext);
          sendServerPlayers(commandContext.getSource());
          return 1;
        });
    server.getCommandManager().register(new BrigadierCommand(rootNode));
  }

  private void sendServerPlayers(final CommandSource target) {
    if (!(target instanceof Player p)) {
      System.out.println("returning cause not a player");
      return;
    }

    ServerConnection serverConnection = p.getCurrentServer().orElse(null);
    System.out.println("Server conn: " + serverConnection);

    if (serverConnection == null) {
      return;
    }

    List<String> usernames = new ArrayList<>();

    if (this.server.getRedisManager().isEnabled()) {
      usernames = this.server.getRedisManager().getConnectedPlayerNames(serverConnection.getServerInfo().getName());
      System.out.println("usernames: " + usernames);
    } else {
      for (Player player : serverConnection.getServer().getPlayersConnected()) {
        usernames.add(player.getUsername());
      }
    }

    if (usernames.isEmpty()) {
      return;
    }

    mapUsernames(target, usernames, serverConnection.getServerInfo());
  }

  static void mapUsernames(CommandSource target, List<String> usernames, ServerInfo serverInfo) {
    usernames.stream()
          .reduce((a, b) -> a + ", " + b)
          .ifPresent(playerList -> {
            final TranslatableComponent.Builder builder = Component.translatable()
                .key("velocity.command.glist-server")
                .arguments(
                    Component.text(serverInfo.getName()),
                    Component.text(usernames.size()),
                    Component.text(playerList)
                );
            target.sendMessage(Component.text("There are currently " + usernames.size() + " players connected to your server"));
            target.sendMessage(builder.build());
          });
  }
}
