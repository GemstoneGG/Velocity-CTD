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

import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.chat.ChatHandler;

/**
 * A handler for processing legacy chat packets, implementing {@link ChatHandler}.
 *
 * <p>The {@code LegacyChatHandler} is responsible for handling and processing chat messages
 * sent using {@link LegacyChatPacket}. This class provides the necessary logic for
 * processing chat data using legacy Minecraft chat formats.</p>
 */
public class LegacyChatHandler implements ChatHandler<LegacyChatPacket> {

  /**
   * The Velocity server instance used to fire events and manage server state.
   */
  private final VelocityServer server;

  /**
   * The player sending the legacy chat message.
   */
  private final ConnectedPlayer player;

  /**
   * Constructs a new {@code LegacyChatHandler} for the specified server and player.
   *
   * @param server the proxy server instance
   * @param player the player associated with the incoming chat packet
   */
  public LegacyChatHandler(final VelocityServer server, final ConnectedPlayer player) {
    this.server = server;
    this.player = player;
  }

  /**
   * Returns the class of chat packets that this handler processes.
   *
   * <p>Used by the chat framework to associate this handler with {@link LegacyChatPacket}
   * instances for legacy-format message processing.</p>
   *
   * @return the {@code LegacyChatPacket} class
   */
  @Override
  public Class<LegacyChatPacket> packetClass() {
    return LegacyChatPacket.class;
  }

  /**
   * Handles a player-sent legacy chat packet internally.
   *
   * <p>This method performs the following steps:</p>
   * <ul>
   *   <li>Ensures the player is connected to a backend server.</li>
   *   <li>Fires a {@link PlayerChatEvent} to allow plugins to observe or modify the message.</li>
   *   <li>If the message is not cancelled, the message (possibly modified) is sent to the backend server
   *       using the legacy chat format.</li>
   * </ul>
   *
   * @param packet the incoming legacy-format chat packet from the player
   */
  @Override
  public void handlePlayerChatInternal(final LegacyChatPacket packet) {
    MinecraftConnection serverConnection = player.ensureAndGetCurrentServer().ensureConnected();
    if (serverConnection == null) {
      return;
    }

    this.server.getEventManager().fire(new PlayerChatEvent(this.player, packet.getMessage()))
        .whenComplete((chatEvent, throwable) -> {
          if (!chatEvent.getResult().isAllowed()) {
            return;
          }

          serverConnection.write(this.player.getChatBuilderFactory().builder()
              .message(chatEvent.getResult().getMessage().orElse(packet.getMessage())).toServer());
        });
  }
}
