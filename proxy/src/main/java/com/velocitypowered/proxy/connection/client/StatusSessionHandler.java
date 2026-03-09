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

import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.util.VelocityInboundConnection;
import com.velocitypowered.proxy.protocol.packet.LegacyDisconnect;
import com.velocitypowered.proxy.protocol.packet.LegacyPingPacket;
import com.velocitypowered.proxy.protocol.packet.StatusPingPacket;
import com.velocitypowered.proxy.protocol.packet.StatusRequestPacket;
import com.velocitypowered.proxy.protocol.packet.StatusResponsePacket;
import com.velocitypowered.proxy.util.except.QuietRuntimeException;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles server list ping packets from a client.
 */
public class StatusSessionHandler implements MinecraftSessionHandler {

  /**
   * The logger for this class.
   */
  private static final Logger logger = LogManager.getLogger(StatusSessionHandler.class);

  /**
   * Thrown when a status ping is received after the expected request window.
   */
  private static final QuietRuntimeException EXPECTED_AWAITING_REQUEST = new QuietRuntimeException(
      "Expected connection to be awaiting status request");

  /**
   * The Velocity server instance.
   */
  private final VelocityServer server;

  /**
   * The Minecraft connection associated with this session.
   */
  private final MinecraftConnection connection;

  /**
   * The inbound connection abstraction.
   */
  private final VelocityInboundConnection inbound;

  /**
   * Whether a ping has already been received.
   */
  private boolean pingReceived = false;

  StatusSessionHandler(final VelocityServer server, final VelocityInboundConnection inbound) {
    this.server = server;
    this.connection = inbound.getConnection();
    this.inbound = inbound;
  }

  /**
   * Called when this session handler is activated.
   *
   * <p>If ping logging is enabled in the configuration, this logs the fact that
   * the client has initiated a status ping and identifies the protocol version used.</p>
   */
  @Override
  public void activated() {
    if (server.getConfiguration().isShowPingRequests()) {
      logger.info("{} is pinging the server with version {}", this.inbound,
          this.connection.getProtocolVersion());
    }
  }

  /**
   * Handles a {@link LegacyPingPacket} sent by a pre-1.7 client.
   *
   * <p>This method ensures only one ping request is processed per session. It then generates a ping response
   * and closes the connection after responding.</p>
   *
   * @param packet the legacy ping packet
   * @return {@code true} if the packet was handled
   * @throws QuietRuntimeException if a ping was already received for this session
   */
  @Override
  public boolean handle(final LegacyPingPacket packet) {
    if (this.pingReceived) {
      throw EXPECTED_AWAITING_REQUEST;
    }

    this.pingReceived = true;

    server.getServerListPingHandler().getInitialPing(this.inbound)
        .thenCompose(ping -> server.getEventManager().fire(new ProxyPingEvent(inbound, ping)))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            connection.closeWith(LegacyDisconnect.fromServerPing(event.getPing(), packet.getVersion()));
          } else {
            connection.close();
          }
        }, connection.eventLoop())
        .exceptionally((ex) -> {
          logger.error("Exception while handling legacy ping {}", packet, ex);
          return null;
        });

    return true;
  }

  /**
   * Handles a {@link StatusPingPacket} which completes the 1.7+ ping process.
   *
   * <p>This closes the connection after sending the pong response (handled by Netty).</p>
   *
   * @param packet the status ping packet
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final StatusPingPacket packet) {
    connection.closeWith(packet);
    return true;
  }

  /**
   * Handles a {@link StatusRequestPacket} from modern (1.7+) clients requesting status info.
   *
   * <p>This triggers the {@link ProxyPingEvent} and responds with a {@link StatusResponsePacket}
   * if the event is allowed. If not, the connection is closed.</p>
   *
   * @param packet the status request
   * @return {@code true} if the request was processed
   * @throws QuietRuntimeException if a ping was already received for this session
   */
  @Override
  public boolean handle(final StatusRequestPacket packet) {
    if (this.pingReceived) {
      throw EXPECTED_AWAITING_REQUEST;
    }

    this.pingReceived = true;

    this.server.getServerListPingHandler().getInitialPing(inbound)
        .thenCompose(ping -> server.getEventManager().fire(new ProxyPingEvent(inbound, ping)))
        .thenAcceptAsync(
            (event) -> {
              if (event.getResult().isAllowed()) {
                final StringBuilder json = new StringBuilder();
                VelocityServer.getPingGsonInstance(connection.getProtocolVersion())
                        .toJson(event.getPing(), json);
                connection.write(new StatusResponsePacket(json));
              } else {
                connection.close();
              }
            },
            connection.eventLoop())
        .exceptionally((ex) -> {
          logger.error("Exception while handling status request {}", packet, ex);
          return null;
        });

    return true;
  }

  /**
   * Called when an unknown packet is received during a status ping session.
   *
   * <p>This forcefully closes the connection because status sessions only expect a
   * small, fixed set of packet types.</p>
   *
   * @param buf the buffer containing the unknown packet
   */
  @Override
  public void handleUnknown(final ByteBuf buf) {
    // What even is going on?
    connection.close(true);
  }

  private enum State {

    /**
     * Indicates that the server is waiting for a status request or legacy ping from the client.
     */
    AWAITING_REQUEST,

    /**
     * Indicates that a status or legacy ping request has been received from the client.
     */
    RECEIVED_REQUEST
  }
}
