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

package com.velocitypowered.proxy.server;

import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.HandshakePacket;
import com.velocitypowered.proxy.protocol.packet.StatusRequestPacket;
import com.velocitypowered.proxy.protocol.packet.StatusResponsePacket;
import io.netty.channel.EventLoop;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Session handler used to implement {@link VelocityRegisteredServer#ping(EventLoop,
 * com.velocitypowered.api.proxy.server.PingOptions)}.
 */
public class PingSessionHandler implements MinecraftSessionHandler {

  /**
   * The future that will be completed with the resulting {@link ServerPing},
   * or exceptionally if the ping fails.
   */
  private final CompletableFuture<ServerPing> result;

  /**
   * The target server being pinged.
   */
  private final RegisteredServer server;

  /**
   * The underlying Minecraft protocol connection to the server.
   */
  private final MinecraftConnection connection;

  /**
   * The protocol version used for the ping.
   */
  private final ProtocolVersion version;

  /**
   * Indicates whether the ping operation completed successfully.
   */
  private boolean completed = false;

  /**
   * The virtual host string to include in the handshake.
   * If blank, defaults to the target server's IP.
   */
  private final String virtualHostString;

  PingSessionHandler(final CompletableFuture<ServerPing> result, final RegisteredServer server,
                     final MinecraftConnection connection, final ProtocolVersion version, final String virtualHostString) {
    this.result = result;
    this.server = server;
    this.connection = connection;
    this.version = version;
    this.virtualHostString = virtualHostString;
  }

  /**
   * Called when this session handler is activated.
   *
   * <p>Sends a {@link HandshakePacket} and a {@link StatusRequestPacket} to the server using the
   * provided protocol version and virtual host information. The session state is updated to
   * {@link StateRegistry#STATUS}, and all packets are flushed to initiate the ping sequence.</p>
   */
  @Override
  public void activated() {
    HandshakePacket handshake = new HandshakePacket();
    handshake.setIntent(HandshakeIntent.STATUS);
    handshake.setServerAddress(this.virtualHostString == null || this.virtualHostString.isEmpty()
        ? server.getServerInfo().getAddress().getHostString() : this.virtualHostString);
    handshake.setPort(server.getServerInfo().getAddress().getPort());
    handshake.setProtocolVersion(version);
    connection.delayedWrite(handshake);

    connection.setActiveSessionHandler(StateRegistry.STATUS);
    connection.setState(StateRegistry.STATUS);
    connection.delayedWrite(StatusRequestPacket.INSTANCE);

    connection.flush();
  }

  /**
   * Handles a {@link StatusResponsePacket} containing the server's ping data.
   *
   * <p>Parses the response into a {@link ServerPing} using the appropriate GSON instance
   * for the client's protocol version, completes the result future, and closes the connection.</p>
   *
   * @param packet the status response packet from the remote server
   * @return {@code true} once processed
   */
  @Override
  public boolean handle(final StatusResponsePacket packet) {
    // All good!
    completed = true;
    connection.close(true);

    ServerPing ping = VelocityServer.getPingGsonInstance(version).fromJson(packet.getStatus(),
        ServerPing.class);
    result.complete(ping);
    return true;
  }

  /**
   * Called when the connection is closed before receiving a {@link StatusResponsePacket}.
   *
   * <p>If the ping was not completed, the result future is completed exceptionally with
   * an {@link IOException} indicating unexpected disconnection.</p>
   */
  @Override
  public void disconnected() {
    if (!completed) {
      result.completeExceptionally(new IOException("Unexpectedly disconnected from remote server"));
    }
  }

  /**
   * Called when an exception occurs during the ping process.
   *
   * <p>The result future is completed exceptionally with the provided {@code throwable}
   * and the session is marked as completed to avoid duplicate results.</p>
   *
   * @param throwable the exception that occurred during ping
   */
  @Override
  public void exception(final Throwable throwable) {
    completed = true;
    result.completeExceptionally(throwable);
  }
}
