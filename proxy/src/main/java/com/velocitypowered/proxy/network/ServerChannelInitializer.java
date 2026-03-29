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

package com.velocitypowered.proxy.network;

import static com.velocitypowered.proxy.network.Connections.FRAME_DECODER;
import static com.velocitypowered.proxy.network.Connections.FRAME_ENCODER;
import static com.velocitypowered.proxy.network.Connections.LEGACY_PING_DECODER;
import static com.velocitypowered.proxy.network.Connections.LEGACY_PING_ENCODER;
import static com.velocitypowered.proxy.network.Connections.MINECRAFT_DECODER;
import static com.velocitypowered.proxy.network.Connections.MINECRAFT_ENCODER;
import static com.velocitypowered.proxy.network.Connections.READ_TIMEOUT;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.HandshakeSessionHandler;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.LegacyPingDecoder;
import com.velocitypowered.proxy.protocol.netty.LegacyPingEncoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintFrameDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintLengthEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.http.HttpClient;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Server channel initializer.
 */
public class ServerChannelInitializer extends ChannelInitializer<Channel> {

  /**
   * The Velocity server instance used to configure this channel.
   */
  private final VelocityServer server;

  /**
   * The shared {@link HttpClient} amongst all {@link HandshakeSessionHandler}s this class creates.
   */
  private volatile @MonotonicNonNull HttpClient httpClient;

  /**
   * Constructs a new {@link ServerChannelInitializer}.
   *
   * @param server the Velocity server instance
   */
  public ServerChannelInitializer(final VelocityServer server) {
    this.server = server;
  }

  /**
   * Initializes the Netty pipeline for a new client channel.
   *
   * <p>This configures the following handlers in order:</p>
   * <ul>
   *   <li>{@code LEGACY_PING_DECODER} – handles legacy 1.6 ping requests</li>
   *   <li>{@code FRAME_DECODER} – decodes VarInt length-prefixed packets from the client</li>
   *   <li>{@code READ_TIMEOUT} – disconnects idle clients after the configured timeout</li>
   *   <li>{@code LEGACY_PING_ENCODER} – encodes legacy ping responses</li>
   *   <li>{@code FRAME_ENCODER} – encodes outgoing packets with VarInt length prefix</li>
   *   <li>{@code MINECRAFT_DECODER} – decodes Minecraft protocol packets (serverbound)</li>
   *   <li>{@code MINECRAFT_ENCODER} – encodes Minecraft protocol packets (clientbound)</li>
   *   <li>{@code HANDLER} – wraps the connection in a {@link MinecraftConnection}</li>
   * </ul>
   *
   * <p>If PROXY protocol is enabled in configuration, a {@link HAProxyMessageDecoder} is added
   * at the beginning of the pipeline to extract the real client IP address.</p>
   *
   * @param ch the Netty channel to initialize
   */
  @Override
  @EnsuresNonNull("httpClient")
  protected void initChannel(final Channel ch) {
    if (this.httpClient == null) {
      synchronized (this) {
        if (this.httpClient == null) {
          this.httpClient = server.createHttpClient();
        }
      }
    }

    ch.pipeline()
        .addLast(LEGACY_PING_DECODER, new LegacyPingDecoder())
        .addLast(FRAME_DECODER, new MinecraftVarintFrameDecoder(ProtocolUtils.Direction.SERVERBOUND))
        .addLast(READ_TIMEOUT, new ReadTimeoutHandler(this.server.getConfiguration().getReadTimeout(), TimeUnit.MILLISECONDS))
        .addLast(LEGACY_PING_ENCODER, LegacyPingEncoder.INSTANCE)
        .addLast(FRAME_ENCODER, MinecraftVarintLengthEncoder.INSTANCE)
        .addLast(MINECRAFT_DECODER, new MinecraftDecoder(ProtocolUtils.Direction.SERVERBOUND))
        .addLast(MINECRAFT_ENCODER, new MinecraftEncoder(ProtocolUtils.Direction.CLIENTBOUND));

    final MinecraftConnection connection = new MinecraftConnection(ch, this.server);
    connection.setActiveSessionHandler(StateRegistry.HANDSHAKE,
        new HandshakeSessionHandler(connection, this.server, this.httpClient));
    ch.pipeline().addLast(Connections.HANDLER, connection);

    if (this.server.getConfiguration().isProxyProtocol()) {
      ch.pipeline().addFirst(new HAProxyMessageDecoder());
    }
  }
}
