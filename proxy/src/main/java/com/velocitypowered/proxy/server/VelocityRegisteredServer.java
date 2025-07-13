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

package com.velocitypowered.proxy.server;

import static com.velocitypowered.proxy.network.Connections.FRAME_DECODER;
import static com.velocitypowered.proxy.network.Connections.FRAME_ENCODER;
import static com.velocitypowered.proxy.network.Connections.HANDLER;
import static com.velocitypowered.proxy.network.Connections.MINECRAFT_DECODER;
import static com.velocitypowered.proxy.network.Connections.MINECRAFT_ENCODER;
import static com.velocitypowered.proxy.network.Connections.READ_TIMEOUT;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.PluginMessageEncoder;
import com.velocitypowered.api.proxy.player.PlayerInfo;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintFrameDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintLengthEncoder;
import com.velocitypowered.proxy.protocol.util.ByteBufDataOutput;
import com.velocitypowered.proxy.queue.QueueManagerRedisImpl;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.redis.multiproxy.RemotePlayerInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a server registered on the proxy.
 */
public class VelocityRegisteredServer implements RegisteredServer, ForwardingAudience {

  /**
   * The Velocity server instance associated with this registered server,
   * or {@code null} if constructed without full proxy context (e.g., testing).
   */
  private final @Nullable VelocityServer server;

  /**
   * The information identifying this backend server, including name and address.
   */
  private final ServerInfo serverInfo;

  /**
   * The players currently connected to this server from this proxy instance,
   * indexed by their UUIDs.
   */
  private final Map<UUID, ConnectedPlayer> players = new ConcurrentHashMap<>();

  /**
   * Constructs a {@link VelocityRegisteredServer} instance.
   *
   * @param server the proxy server
   * @param serverInfo info on this server
   */
  public VelocityRegisteredServer(@Nullable final VelocityServer server, final ServerInfo serverInfo) {
    this.server = server;
    this.serverInfo = Preconditions.checkNotNull(serverInfo, "serverInfo");
  }

  @Override
  public final ServerInfo getServerInfo() {
    return serverInfo;
  }

  @Override
  public final Collection<Player> getPlayersConnected() {
    return ImmutableList.copyOf(players.values());
  }

  @Override
  public final long getTotalPlayerCount() {
    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      int amount = 0;

      for (RemotePlayerInfo info : this.server.getMultiProxyHandler().getAllPlayers()) {
        if (info.getServerName() != null && info.getServerName().equalsIgnoreCase(getServerInfo().getName())) {
          amount++;
        }
      }

      return amount;
    } else {
      return getPlayersConnected().size();
    }
  }

  @Override
  public final List<PlayerInfo> getPlayerInfo() {
    if (this.server == null || !this.server.getMultiProxyHandler().isRedisEnabled()) {
      List<PlayerInfo> info = new ArrayList<>();
      players.forEach((uuid, player) -> info.add(new PlayerInfo(player.getUsername(), player.getUniqueId())));
      return info;
    }

    List<PlayerInfo> info = new ArrayList<>();
    for (RemotePlayerInfo i : this.server.getMultiProxyHandler().getAllPlayers()) {
      if (i.getServerName() != null && i.getServerName().equalsIgnoreCase(getServerInfo().getName())) {
        info.add(new PlayerInfo(i.getName(), i.getUuid()));
      }
    }

    return info;
  }

  /**
   * Returns the number of players currently connected to this server
   * from this proxy instance only (does not include Redis-based players).
   *
   * @return the local player count
   */
  public long getPlayerCount() {
    return this.players.size();
  }

  /**
   * Gets the {@link ConnectedPlayer} associated with the given UUID
   * on this registered server.
   *
   * @param uuid the UUID of the player
   * @return the connected player, or {@code null} if not found
   */
  public ConnectedPlayer getPlayer(final UUID uuid) {
    return players.get(uuid);
  }

  @Override
  public final CompletableFuture<ServerPing> ping(final PingOptions pingOptions) {
    return ping(null, pingOptions);
  }

  @Override
  public final CompletableFuture<ServerPing> ping() {
    return ping(null, PingOptions.DEFAULT);
  }

  /**
   * Pings the specified server using the specified event {@code loop}, claiming to be {@code
   * version}.
   *
   * @param loop    the event loop to use
   * @param pingOptions the options to apply to this ping
   * @return the server list's ping response
   */
  public CompletableFuture<ServerPing> ping(@Nullable final EventLoop loop, final PingOptions pingOptions) {
    if (server == null) {
      throw new IllegalStateException("No Velocity proxy instance available");
    }

    CompletableFuture<ServerPing> pingFuture = new CompletableFuture<>();
    server.createBootstrap(loop).handler(new ChannelInitializer<>() {
      @Override
      protected void initChannel(@NotNull final Channel ch) {
        ch.pipeline().addLast(FRAME_DECODER, new MinecraftVarintFrameDecoder(ProtocolUtils.Direction.CLIENTBOUND))
            .addLast(READ_TIMEOUT, new ReadTimeoutHandler(
                pingOptions.getTimeout() == 0
                    ? server.getConfiguration().getReadTimeout()
                    : pingOptions.getTimeout(), TimeUnit.MILLISECONDS))
            .addLast(FRAME_ENCODER, MinecraftVarintLengthEncoder.INSTANCE)
            .addLast(MINECRAFT_DECODER, new MinecraftDecoder(ProtocolUtils.Direction.CLIENTBOUND))
            .addLast(MINECRAFT_ENCODER, new MinecraftEncoder(ProtocolUtils.Direction.SERVERBOUND));

        ch.pipeline().addLast(HANDLER, new MinecraftConnection(ch, server));
      }
    }).connect(serverInfo.getAddress()).addListener((ChannelFutureListener) future -> {
      if (future.isSuccess()) {
        MinecraftConnection conn = future.channel().pipeline().get(MinecraftConnection.class);
        PingSessionHandler handler = new PingSessionHandler(pingFuture,
            VelocityRegisteredServer.this, conn, pingOptions.getProtocolVersion(), pingOptions.getVirtualHost());
        conn.setActiveSessionHandler(StateRegistry.HANDSHAKE, handler);
      } else {
        pingFuture.completeExceptionally(future.cause());
      }
    });

    return pingFuture;
  }

  /**
   * Adds the specified player to this server's local player list.
   *
   * @param player the player to add
   */
  public void addPlayer(final ConnectedPlayer player) {
    players.put(player.getUniqueId(), player);
  }

  /**
   * Removes the specified player from this server's local player list.
   *
   * @param player the player to remove
   */
  public void removePlayer(final ConnectedPlayer player) {
    players.remove(player.getUniqueId(), player);
  }

  @Override
  public final boolean sendPluginMessage(final @NotNull ChannelIdentifier identifier, final byte @NotNull [] data) {
    requireNonNull(identifier);
    requireNonNull(data);
    return sendPluginMessage(identifier, Unpooled.wrappedBuffer(data));
  }

  @Override
  public final boolean sendPluginMessage(final @NotNull ChannelIdentifier identifier, final @NotNull PluginMessageEncoder dataEncoder) {
    requireNonNull(identifier);
    requireNonNull(dataEncoder);
    final ByteBuf buf = Unpooled.buffer();
    final ByteBufDataOutput dataInput = new ByteBufDataOutput(buf);
    dataEncoder.encode(dataInput);
    if (buf.isReadable()) {
      return sendPluginMessage(identifier, buf);
    } else {
      buf.release();
      return false;
    }
  }

  /**
   * Sends a plugin message to the server through this connection. The message will be released
   * afterward.
   *
   * @param identifier the channel ID to use
   * @param data       the data
   * @return whether the message was sent
   */
  public boolean sendPluginMessage(final ChannelIdentifier identifier, final ByteBuf data) {
    for (final ConnectedPlayer player : players.values()) {
      final VelocityServerConnection serverConnection = player.getConnectedServer();
      if (serverConnection != null && serverConnection.getConnection() != null
              && serverConnection.getServer() == this) {
        return serverConnection.sendPluginMessage(identifier, data);
      }
    }

    data.release();
    return false;
  }

  @Override
  public final String toString() {
    return "registered server: " + serverInfo;
  }

  @Override
  public final @NonNull Iterable<? extends Audience> audiences() {
    return this.getPlayersConnected();
  }

  /**
   * Gets the queue status from the {@link QueueManagerRedisImpl}
   * directly, to make it work with the old system automatically.
   *
   * @return The queue status of the server
   */
  public ServerQueueStatus getQueueStatus() {
    return this.server.getQueueManager().getQueue(serverInfo.getName());
  }
}
