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

package com.velocitypowered.proxy.protocol.packet.chat.legacy;

import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.chat.RateLimitedCommandHandler;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * A handler for processing legacy commands, implementing {@link RateLimitedCommandHandler}.
 *
 * <p>The {@code LegacyCommandHandler} processes and handles command packets that are sent
 * using {@link LegacyChatPacket}. It provides the necessary logic to support legacy
 * command formats and ensure compatibility with older Minecraft versions.</p>
 */
public class LegacyCommandHandler extends RateLimitedCommandHandler<LegacyChatPacket> {

  /**
   * The player who sent the command.
   */
  private final ConnectedPlayer player;

  /**
   * The Velocity server instance used for plugin execution and backend forwarding.
   */
  private final VelocityServer server;

  /**
   * Constructs a new {@code LegacyCommandHandler} for handling legacy command packets.
   *
   * @param player the connected player issuing the command
   * @param server the Velocity server instance
   */
  public LegacyCommandHandler(final ConnectedPlayer player, final VelocityServer server) {
    super(player, server);
    this.player = player;
    this.server = server;
  }

  /**
   * Returns the class of command packets this handler is responsible for.
   *
   * <p>This identifies the handler as responsible for {@link LegacyChatPacket},
   * which represents command messages in legacy chat format.</p>
   *
   * @return the {@code LegacyChatPacket} class
   */
  @Override
  public Class<LegacyChatPacket> packetClass() {
    return LegacyChatPacket.class;
  }

  /**
   * Handles the execution of a legacy-format command sent by the player.
   *
   * <p>This method performs the following steps:</p>
   * <ul>
   *   <li>Extracts the command string from the {@link LegacyChatPacket} (stripping leading {@code /}).</li>
   *   <li>Fires a {@link CommandExecuteEvent} to allow plugin handling.</li>
   *   <li>Handles denied, rewritten, or forwarded commands based on the event result.</li>
   *   <li>Runs the command locally or sends it to the backend server as appropriate.</li>
   * </ul>
   *
   * @param packet the legacy chat packet containing a command
   */
  @Override
  public void handlePlayerCommandInternal(final LegacyChatPacket packet) {
    String command = packet.getMessage().substring(1);
    queueCommandResult(this.server, this.player, (event, newLastSeenMessages) -> {
      CommandExecuteEvent.CommandResult result = event.getResult();
      if (result == CommandExecuteEvent.CommandResult.denied()) {
        return CompletableFuture.completedFuture(null);
      }

      String commandToRun = result.getCommand().orElse(command);
      if (result.isForwardToServer()) {
        return CompletableFuture.completedFuture(this.player.getChatBuilderFactory().builder()
            .message("/" + commandToRun)
            .toServer());
      }

      return runCommand(this.server, this.player, commandToRun, hasRun -> {
        if (!hasRun) {
          return this.player.getChatBuilderFactory().builder()
              .message(packet.getMessage())
              .asPlayer(this.player)
              .toServer();
        }

        return null;
      });
    }, command, Instant.now(), null, new CommandExecuteEvent.InvocationInfo(CommandExecuteEvent.SignedState.UNSUPPORTED,
          CommandExecuteEvent.Source.PLAYER));
  }
}
