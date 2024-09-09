package com.velocitypowered.proxy.command.builtin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

import java.util.ArrayList;
import java.util.List;

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

                    sendServerPlayers(commandContext.getSource());

                    return 1;
                });
        server.getCommandManager().register(new BrigadierCommand(rootNode));
    }


    private void sendServerPlayers(final CommandSource target) {
        if(!(target instanceof Player p)) return;

        ServerConnection serverConnection = p.getCurrentServer().orElse(null);
        if(serverConnection == null) return;

        List<String> usernames = new ArrayList<>();
        if(this.server.getRedisManager().isEnabled()){
            usernames = this.server.getRedisManager().getConnectedPlayerNames(serverConnection.getServerInfo().getName());
        }else{
            for(Player player : serverConnection.getServer().getPlayersConnected()){
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
                    target.sendMessage(builder.build());
                });
    }
}
