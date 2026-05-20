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

package com.velocitypowered.proxy.protocol.packet.chat;

import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface CommandHandler<T extends MinecraftPacket> {

  Logger logger();

  Class<T> packetClass();

  void handlePlayerCommandInternal(T packet);

  default boolean handlePlayerCommand(MinecraftPacket packet) {
    if (packetClass().isInstance(packet)) {
      handlePlayerCommandInternal(packetClass().cast(packet));
      return true;
    }

    return false;
  }

  default CompletableFuture<MinecraftPacket> runCommand(VelocityServer server,
                                                        ConnectedPlayer player, String command,
                                                        Function<Boolean, MinecraftPacket> hasRunPacketFunction) {
    return server.getCommandManager().executeImmediatelyAsync(player, command)
        .thenApply(hasRunPacketFunction);
  }

  default void queueCommandResult(VelocityServer server, ConnectedPlayer player,
                                  BiFunction<CommandExecuteEvent, LastSeenMessages, CompletableFuture<MinecraftPacket>> futurePacketCreator,
                                  String message, Instant timestamp, @Nullable LastSeenMessages lastSeenMessages,
                                  CommandExecuteEvent.InvocationInfo invocationInfo) {
    CompletableFuture<CommandExecuteEvent> eventFuture = server.getCommandManager().callCommandEvent(player, message, invocationInfo);
    player.getChatQueue().queuePacket(
        newLastSeenMessages -> eventFuture
            .thenComposeAsync(event -> futurePacketCreator.apply(event, newLastSeenMessages))
            .thenApply(pkt -> {
              if (server.getConfiguration().isLogCommandExecutions()) {
                logger().info("{} -> executed command /{}", player, message);
              }

              return pkt;
            }).exceptionally(e -> {
              logger().info("Exception occurred while running command for {}", player.getUsername(), e);
              player.sendMessage(Component.translatable("velocity.command.generic-error", NamedTextColor.RED));
              return null;
            }), timestamp, lastSeenMessages);
  }

  /**
   * Emits the standard fatal log + disconnect sequence used whenever a plugin tries to
   * deny or modify a command that carries a signable / signed component. Centralizing the
   * sequence keeps the wording consistent across handlers, while routing the fatal log
   * through {@link #logger()} ensures the log entry identifies which concrete handler
   * raised it.
   *
   * @param what   verb describing the violation, e.g. {@code "deny"} or {@code "change"}
   * @param player the offending player; will be disconnected after the log entry
   * @param packet the command packet that triggered the violation, included in the log
   */
  default void alterSignableComponentError(String what, ConnectedPlayer player, MinecraftPacket packet) {
    logger().fatal("A plugin tried to {} a command with signable component(s). "
        + "This is not supported. Disconnecting player {}. Command packet: {}",
        what, player.getUsername(), packet);
    player.disconnect(Component.text(
        "A proxy plugin caused an illegal protocol state. "
            + "Contact your network administrator."));
  }
}
