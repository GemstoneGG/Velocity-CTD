/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * Implements Velocity-CTD's {@code /<server_name>} commands.
 */
public class SlashServerCommand implements BuiltinCommandDefinition {

  public static Function<VelocityServer, SlashServerCommand> factory(String serverName, String commandLabel) {
    return proxyServer -> new SlashServerCommand(proxyServer, serverName, commandLabel);
  }

  private final VelocityServer server;
  private final VelocityRegisteredServer registeredServer;
  private final String commandLabel;

  public SlashServerCommand(VelocityServer server, String targetServerName, String commandLabel) {
    this.server = server;
    this.registeredServer = this.server.getServer(targetServerName)
        .orElseThrow(() -> new IllegalArgumentException("Target server '" + targetServerName + "' does not exist."));
    this.commandLabel = commandLabel;
  }

  @Override
  public String label() {
    return commandLabel;
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
        .literalArgumentBuilder(label())
        .requires(src -> src instanceof ConnectedPlayer && CommandUtils.checkServerPermissions(registeredServer, src))
        .executes(this::send);

    return new BrigadierCommand(rootNode);
  }

  private int send(CommandContext<CommandSource> ctx) {
    ConnectedPlayer player = (ConnectedPlayer) ctx.getSource();

    VelocityServerConnection connection = player.getCurrentServer().orElse(null);
    if (connection != null && connection.getServer() == registeredServer) {
      player.sendMessage(Component.translatable("velocity.command.slashserver.already-connected"));
      return 0;
    }

    CommandUtils.sendOrQueue(server, player, registeredServer);
    return SINGLE_SUCCESS;
  }
}
