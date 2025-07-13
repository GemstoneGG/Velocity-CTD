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

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import net.kyori.adventure.text.Component;

/**
 * Abstract base class for handling rate-limited player command packets.
 *
 * <p>Subclasses should implement {@link #handlePlayerCommandInternal(MinecraftPacket)} to define
 * how individual command packets are processed. Rate limiting is enforced to prevent abuse.</p>
 *
 * @param <T> the type of {@link MinecraftPacket} this handler processes
 */
public abstract class RateLimitedCommandHandler<T extends MinecraftPacket> implements CommandHandler<T> {

  /**
   * The player who issued the command.
   */
  private final Player player;

  /**
   * The Velocity server instance, used to retrieve configuration and rate limiter state.
   */
  private final VelocityServer velocityServer;

  /**
   * The number of consecutive failed command attempts due to rate limiting.
   */
  private int failedAttempts;

  /**
   * Constructs a new {@code RateLimitedCommandHandler} for the specified player and server.
   *
   * @param player the player sending the command
   * @param velocityServer the Velocity server managing command processing and rate limiting
   */
  protected RateLimitedCommandHandler(final Player player, final VelocityServer velocityServer) {
    this.player = player;
    this.velocityServer = velocityServer;
  }

  @Override
  public final boolean handlePlayerCommand(final MinecraftPacket packet) {
    if (packetClass().isInstance(packet)) {
      if (!velocityServer.getCommandRateLimiter().attempt(player.getUniqueId())) {
        if (velocityServer.getConfiguration().isKickOnCommandRateLimit() && failedAttempts++
                >= velocityServer.getConfiguration().getKickAfterRateLimitedCommands()) {
          player.disconnect(Component.translatable("velocity.kick.command-rate-limit"));
        }

        if (velocityServer.getConfiguration().isForwardCommandsIfRateLimited()) {
          return false; // Send the packet to the server
        }
      } else {
        failedAttempts = 0;
      }

      handlePlayerCommandInternal(packetClass().cast(packet));
      return true;
    }

    return false;
  }
}
