/*
 * Copyright (C) 2022-2023 Velocity Contributors
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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

  Logger logger = LogManager.getLogger(CommandHandler.class);

  Class<T> packetClass();

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
   */
  default void queueCommandResult(VelocityServer server, ConnectedPlayer player,
      BiFunction<CommandExecuteEvent, LastSeenMessages, CompletableFuture<MinecraftPacket>> futurePacketCreator,
      String message, Instant timestamp, @Nullable LastSeenMessages lastSeenMessages,
                                  CommandExecuteEvent.InvocationInfo invocationInfo) {
    CompletableFuture<CommandExecuteEvent> eventFuture = server.getCommandManager().callCommandEvent(player, message,
            invocationInfo);
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
              player.sendMessage(
                  Component.translatable("velocity.command.generic-error", NamedTextColor.RED));
              return null;
            }), timestamp, lastSeenMessages);
  }

  /**
   * Checks whether the specified player has exceeded the command rate limit.
   *
   * <p>This method verifies if a player is issuing commands too frequently
   * and enforces a rate limit to prevent spam. If the player exceeds the
   * allowed rate, the command execution may be blocked or delayed.</p>
   *
   * @param player The player executing the command.
   * @return {@code true} if the player is within the allowed rate limit,
   *         {@code false} if the command execution should be restricted.
   */
  default boolean checkCommandRateLimit(Player player, long limit) {
    if (limit < 0) {
      return true;
    }

    long newAmount;
    try {
      newAmount = COMMANDS_IN_LAST_SECOND.get(player.getUniqueId(), () -> 0L) + 1;
    } catch (ExecutionException e) {
      logger.error("Error while checking command rate limit for {}", player.getUsername(), e);
      return true;
    }

    if (newAmount > limit) {
      player.disconnect(Component.text("Sending commands too quickly"));
      return false;
    }

    COMMANDS_IN_LAST_SECOND.put(player.getUniqueId(), newAmount);
    return true;
  }

  /**
   * Tracks the number of commands executed by each player within the last second.
   * Entries expire after 1 second to enforce rate limits.
   */
  Cache<UUID, Long> COMMANDS_IN_LAST_SECOND = CacheBuilder.newBuilder()
      .expireAfterWrite(1, TimeUnit.SECONDS)
      .build();
}
