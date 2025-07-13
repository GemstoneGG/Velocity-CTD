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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a handler for processing commands associated with specific types of Minecraft packets.
 * This interface is generic and allows for handling different packet types that extend
 * {@link MinecraftPacket}.
 *
 * @param <T> the type of packet that this handler is responsible for, which
 *            must extend {@link MinecraftPacket}
 */
public interface CommandHandler<T extends MinecraftPacket> {

  /**
   * Shared logger for command handler operations.
   */
  Logger logger = LogManager.getLogger(CommandHandler.class);

  /**
   * Returns the class of packet this handler supports.
   *
   * @return the supported packet class
   */
  Class<T> packetClass();

  /**
   * Handles a command-specific packet of type {@code T}.
   *
   * <p>This method must be implemented by the handler to define how to process
   * an incoming command-related packet.</p>
   *
   * @param packet the packet to process
   */
  void handlePlayerCommandInternal(T packet);

  /**
   * Handles a player command associated with a given {@link MinecraftPacket}.
   * This default method provides a mechanism for processing commands related to player
   * actions within the game.
   *
   * @param packet the {@link MinecraftPacket} representing the player command to be handled
   * @return {@code true} if the command was successfully handled, {@code false} otherwise
   */
  default boolean handlePlayerCommand(MinecraftPacket packet) {
    if (packetClass().isInstance(packet)) {
      handlePlayerCommandInternal(packetClass().cast(packet));
      return true;
    }

    return false;
  }

  /**
   * Immediately runs a command through the proxy’s command system and provides
   * a result to the given function.
   *
   * <p>This method bypasses event dispatch and runs the command synchronously
   * in a future.</p>
   *
   * @param server the proxy server
   * @param player the player executing the command
   * @param command the command string to execute
   * @param hasRunPacketFunction function to convert result into a {@link MinecraftPacket}
   * @return future that resolves with the resulting packet
   */
  default CompletableFuture<MinecraftPacket> runCommand(VelocityServer server,
                                                        ConnectedPlayer player, String command,
                                                        Function<Boolean, MinecraftPacket> hasRunPacketFunction) {
    return server.getCommandManager().executeImmediatelyAsync(player, command)
        .thenApply(hasRunPacketFunction);
  }

  /**
   * Queues the result of a player command for execution, managing the future result of the command.
   * This method is designed to interact with the {@link VelocityServer}
   * and {@link ConnectedPlayer} to
   * handle the process of command execution, queuing, and response handling.
   *
   * @param server the {@link VelocityServer} instance responsible for managing the
   *               command execution
   * @param player the {@link ConnectedPlayer} who initiated the command
   * @param futurePacketCreator a {@link BiFunction} that creates a future packet based on
   *                            the {@link CommandExecuteEvent}
   *                            and {@link LastSeenMessages}, which will be used for sending
   *                            a command result
   * @param message the command message that the player sent
   * @param timestamp the {@link Instant} when the command was executed
   * @param lastSeenMessages the {@link LastSeenMessages} object containing the messages last
   *                         seen by the player,
   *                         or {@code null} if not applicable
   * @param invocationInfo signing metadata for the event dispatch
   */
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
                logger.info("{} -> executed command /{}", player, message);
              }

              return pkt;
            }).exceptionally(e -> {
              logger.info("Exception occurred while running command for {}", player.getUsername(), e);
              player.sendMessage(Component.translatable("velocity.command.generic-error", NamedTextColor.RED));
              return null;
            }), timestamp, lastSeenMessages);
  }
}
