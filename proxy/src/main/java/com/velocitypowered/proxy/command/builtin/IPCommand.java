/*
 * Copyright (C) 2018-2025 Velocity Contributors
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
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

import java.util.Optional;

/**
 * Implements Velocity's {@code /ip} command.
 */
public class IPCommand {

    private final VelocityServer server;

    public IPCommand(final VelocityServer server) {
        this.server = server;
    }

    public BrigadierCommand register(final boolean isIPCommandEnabled) {
        if (!isIPCommandEnabled) {
            return null;
        }

        final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
                .literalArgumentBuilder("ip")
                .requires(source ->
                        source.getPermissionValue("velocity.command.ip") == Tristate.TRUE)
                .executes(ctx -> VelocityCommands.emitUsage(ctx, "ip"));
        final RequiredArgumentBuilder<CommandSource, String> playerNode = BrigadierCommand
                .requiredArgumentBuilder("player", StringArgumentType.word())
                .suggests((ctx, builder) -> VelocityCommands.suggestPlayer(server, ctx, builder, true))
                .executes(this::get_ip_address);

        rootNode.then(playerNode);
        return new BrigadierCommand(rootNode);
    }

    private int get_ip_address(final CommandContext<CommandSource> context) {

        final String player = context.getArgument("player", String.class);
        final Optional<Player> maybePlayer = server.getPlayer(player);
        if (maybePlayer.isEmpty()) {
            context.getSource().sendMessage(
                    CommandMessages.PLAYER_NOT_FOUND.arguments(Argument.string("player", player))
            );

            return 0;
        }

        Player p = maybePlayer.get();
        ServerConnection connection = p.getCurrentServer().orElse(null);
        if (connection == null) {
            context.getSource().sendMessage(
                    Component.translatable("velocity.command.ip.no-server", NamedTextColor.YELLOW)
            );

            return 0;
        }

        RegisteredServer server = connection.getServer();
        if (server == null) {
            context.getSource().sendMessage(
                    Component.translatable("velocity.command.ip.no-server", NamedTextColor.YELLOW)
            );

            return 0;
        }

        context.getSource().sendMessage(
                Component.translatable("velocity.command.ip.message", NamedTextColor.YELLOW)
                        .arguments(
                                Argument.string("player", p.getUsername()),
                                Argument.string("ip_address", p.getRemoteAddress().getAddress().getHostAddress())));

        return Command.SINGLE_SUCCESS;
    }

    private int findMultiProxy(final CommandContext<CommandSource> context) {
        final String player = context.getArgument("player", String.class);
        if (server.getMultiProxyHandler().isPlayerOnline(player)) {
            context.getSource().sendMessage(
                    CommandMessages.PLAYER_NOT_FOUND.arguments(Argument.string("player", player))
            );

            return 0;
        }

        RemotePlayerInfo info = server.getMultiProxyHandler().getPlayerInfo(player);

        if (info.getServerName() == null) {
            context.getSource().sendMessage(
                    Component.translatable("velocity.command.ip.no-server", NamedTextColor.YELLOW)
            );

            return 0;
        }

        RegisteredServer server = this.server.getServer(info.getServerName()).orElse(null);
        if (server == null) {
            context.getSource().sendMessage(
                    Component.translatable("velocity.command.ip.no-server", NamedTextColor.YELLOW)
            );

            return 0;
        }

        context.getSource().sendMessage(
                Component.translatable("velocity.command.ip.message", NamedTextColor.YELLOW)
                        .arguments(
                                Argument.string("player", info.getUsername()),
                                Argument.string("ip_address", server.getServerInfo().getAddress().toString())));

        return Command.SINGLE_SUCCESS;
    }
}
