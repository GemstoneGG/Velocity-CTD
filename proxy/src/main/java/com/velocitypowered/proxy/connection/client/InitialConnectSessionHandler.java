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

package com.velocitypowered.proxy.connection.client;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.backend.BungeeCordMessageResponder;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles the play state between exiting the login phase and establishing the first connection
 * to a backend server.
 */
public class InitialConnectSessionHandler implements MinecraftSessionHandler {

  /**
   * The logger instance for logging events related to {@link InitialConnectSessionHandler}.
   */
  private static final Logger logger = LogManager.getLogger(InitialConnectSessionHandler.class);

  /**
   * The player associated with this session.
   */
  private final ConnectedPlayer player;

  /**
   * The Velocity server instance.
   */
  private final VelocityServer server;

  InitialConnectSessionHandler(final ConnectedPlayer player, final VelocityServer server) {
    this.player = player;
    this.server = server;
  }

  /**
   * Handles a {@link PluginMessagePacket} sent by the client during the initial connection state.
   *
   * <p>If the player has an in-flight backend connection, this method attempts to:
   * <ul>
   *   <li>Handle the packet using the current connection phase (e.g., Forge handshake).</li>
   *   <li>Process BungeeCord-compatible plugin messages.</li>
   *   <li>Fire a {@link PluginMessageEvent} for known registered channels.</li>
   * </ul>
   * If the channel is unknown, the packet is forwarded as-is to the backend server.</p>
   *
   * @param packet the plugin message sent by the client
   * @return {@code true} always
   */
  @Override
  public boolean handle(final PluginMessagePacket packet) {
    VelocityServerConnection serverConn = player.getConnectionInFlight();
    if (serverConn != null) {
      if (player.getPhase().handle(player, packet, serverConn)) {
        return true;
      }

      if (BungeeCordMessageResponder.isBungeeCordMessage(packet)) {
        return true;
      }

      ChannelIdentifier id = server.getChannelRegistrar().getFromId(packet.getChannel());
      if (id == null) {
        serverConn.ensureConnected().write(packet.retain());
        return true;
      }

      byte[] copy = ByteBufUtil.getBytes(packet.content());
      PluginMessageEvent event = new PluginMessageEvent(serverConn, serverConn.getPlayer(), id, copy);
      server.getEventManager().fire(event)
          .thenAcceptAsync(pme -> {
            if (pme.getResult().isAllowed() && serverConn.isActive()) {
              PluginMessagePacket copied = new PluginMessagePacket(packet.getChannel(),
                  Unpooled.wrappedBuffer(copy));
              serverConn.ensureConnected().write(copied);
            }
          }, player.getConnection().eventLoop())
          .exceptionally((ex) -> {
            logger.error("Exception while handling plugin message {}", packet, ex);
            return null;
          });
    }

    return true;
  }

  /**
   * Called when the client disconnects during the initial connection phase.
   *
   * <p>This performs teardown of the player session, releasing any in-flight connections
   * and cleaning up associated state.</p>
   */
  @Override
  public void disconnected() {
    // the user canceled the login process
    player.teardown();
  }
}
