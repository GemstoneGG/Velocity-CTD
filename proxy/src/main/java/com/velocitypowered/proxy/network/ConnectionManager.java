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

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.velocitypowered.api.event.proxy.ListenerBoundEvent;
import com.velocitypowered.api.event.proxy.ListenerCloseEvent;
import com.velocitypowered.api.network.ListenerType;
import com.velocitypowered.natives.util.Natives;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.network.netty.SeparatePoolInetNameResolver;
import com.velocitypowered.proxy.protocol.netty.GameSpyQueryHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.MultithreadEventExecutorGroup;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.Collection;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages endpoints managed by Velocity, along with initializing the Netty event loop group.
 */
public final class ConnectionManager {

  /**
   * The default write buffer watermark used for all Minecraft server channels.
   */
  private static final WriteBufferWaterMark SERVER_WRITE_MARK = new WriteBufferWaterMark(1 << 20, 1 << 21);

  /**
   * The logger instance for this class.
   */
  private static final Logger LOGGER = LogManager.getLogger(ConnectionManager.class, new ParameterizedMessageFactory());

  /**
   * Tracks all active bound endpoints, keyed by their socket address.
   */
  private final Multimap<InetSocketAddress, Endpoint> endpoints = HashMultimap.create();

  /**
   * The {@link TransportType} used for Netty channels (e.g., Epoll, NIO, etc.).
   */
  private final TransportType transportType;

  /**
   * The Netty boss group used to accept new connections.
   */
  private final EventLoopGroup bossGroup;

  /**
   * The Netty worker group used to handle established connections.
   */
  private final EventLoopGroup workerGroup;

  /**
   * A reference to the owning {@link VelocityServer} instance.
   */
  private final VelocityServer server;

  /**
   * Holds the active {@link ServerChannelInitializer}, used for incoming Minecraft connections.
   *
   * <p>This field is public for compatibility with protocol injection systems like ViaVersion.</p>
   */
  public final ServerChannelInitializerHolder serverChannelInitializer;

  /**
   * Holds the active {@link BackendChannelInitializer}, used for backend server connections.
   *
   * <p>This field is public for compatibility with protocol injection systems like ViaVersion.</p>
   */
  public final BackendChannelInitializerHolder backendChannelInitializer;

  /**
   * The name resolver used to resolve DNS names without blocking the Netty threads.
   */
  private final SeparatePoolInetNameResolver resolver;

  /**
   * Initializes the {@code ConnectionManager}.
   *
   * @param server a reference to the Velocity server
   */
  public ConnectionManager(final VelocityServer server) {
    this.server = server;
    this.transportType = TransportType.bestType();
    this.bossGroup = this.transportType.createEventLoopGroup(TransportType.Type.BOSS);
    this.workerGroup = this.transportType.createEventLoopGroup(TransportType.Type.WORKER);
    this.serverChannelInitializer = new ServerChannelInitializerHolder(new ServerChannelInitializer(this.server));
    this.backendChannelInitializer = new BackendChannelInitializerHolder(new BackendChannelInitializer(this.server));
    this.resolver = new SeparatePoolInetNameResolver(GlobalEventExecutor.INSTANCE);
  }

  /**
   * Logs the current Netty channel configuration, including transport type,
   * compression backend, and cipher implementation in use.
   */
  public void logChannelInformation() {
    LOGGER.info("Connections will use {} channels, {} compression, {} ciphers", this.transportType,
        Natives.compress.getLoadedVariant(), Natives.cipher.getLoadedVariant());
  }

  /**
   * Binds a Minecraft listener to the specified {@code address}.
   *
   * @param address the address to bind to
   */
  public void bind(final InetSocketAddress address) {
    final ServerBootstrap bootstrap = new ServerBootstrap()
        .channelFactory(this.transportType.serverSocketChannelFactory)
        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, SERVER_WRITE_MARK)
        .childHandler(this.serverChannelInitializer.get())
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childOption(ChannelOption.IP_TOS, 0x18)
        .localAddress(address);

    if (server.getConfiguration().useTcpFastOpen()) {
      bootstrap.option(ChannelOption.TCP_FASTOPEN, 3);
    }

    if (server.getConfiguration().isEnableReusePort()) {
      // We don't need a boss group, since each worker will bind to the socket
      bootstrap.option(UnixChannelOption.SO_REUSEPORT, true).group(this.workerGroup);
    } else {
      bootstrap.group(this.bossGroup, this.workerGroup);
    }

    final int binds = server.getConfiguration().isEnableReusePort()
        ? ((MultithreadEventExecutorGroup) this.workerGroup).executorCount() : 1;

    for (int bind = 0; bind < binds; bind++) {
      // Wait for each bind to open. If we encounter any errors, don't try to bind again.
      int finalBind = bind;
      ChannelFuture f = bootstrap.bind()
          .addListener((ChannelFutureListener) future -> {
            final Channel channel = future.channel();
            if (future.isSuccess()) {
              this.endpoints.put(address, new Endpoint(channel, ListenerType.MINECRAFT));

              LOGGER.info("Listening on {}", channel.localAddress());

              if (finalBind == 0) {
                // Warn people with console access that HAProxy is in use, see PR: #1436
                if (this.server.getConfiguration().isProxyProtocol()) {
                  LOGGER.warn(
                      "Using HAProxy and listening on {}, please ensure this listener is adequately firewalled.",
                      channel.localAddress());
                }

                // Fire the proxy bound event after the socket is bound
                server.getEventManager().fireAndForget(new ListenerBoundEvent(address, ListenerType.MINECRAFT));
              }
            } else {
              LOGGER.error("Can't bind to {}", address, future.cause());
            }
          });

      f.syncUninterruptibly();

      if (!f.isSuccess()) {
        break;
      }
    }
  }

  /**
   * Binds a GS4 listener to the specified {@code hostname} and {@code port}.
   *
   * @param hostname the hostname to bind to
   * @param port     the port to bind to
   */
  public void queryBind(final String hostname, final int port) {
    InetSocketAddress address = new InetSocketAddress(hostname, port);
    final Bootstrap bootstrap = new Bootstrap()
        .channelFactory(this.transportType.datagramChannelFactory)
        .group(this.workerGroup)
        .handler(new GameSpyQueryHandler(this.server))
        .localAddress(address);
    bootstrap.bind()
        .addListener((ChannelFutureListener) future -> {
          final Channel channel = future.channel();
          if (future.isSuccess()) {
            this.endpoints.put(address, new Endpoint(channel, ListenerType.QUERY));
            LOGGER.info("Listening for GS4 query on {}", channel.localAddress());

            // Fire the proxy bound event after the socket is bound
            server.getEventManager().fireAndForget(
                new ListenerBoundEvent(address, ListenerType.QUERY));
          } else {
            LOGGER.error("Can't bind to {}", bootstrap.config().localAddress(), future.cause());
          }
        });
  }

  /**
   * Creates a TCP {@link Bootstrap} using Velocity's event loops.
   *
   * @param group the event loop group to use. Use {@code null} for the default worker group.
   * @return a new {@link Bootstrap}
   */
  public Bootstrap createWorker(final @Nullable EventLoopGroup group) {
    Bootstrap bootstrap = new Bootstrap()
        .channelFactory(this.transportType.socketChannelFactory)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
            this.server.getConfiguration().getConnectTimeout())
        .group(group == null ? this.workerGroup : group)
        .resolver(this.resolver.asGroup());
    if (server.getConfiguration().useTcpFastOpen()) {
      bootstrap.option(ChannelOption.TCP_FASTOPEN_CONNECT, true);
    }

    return bootstrap;
  }

  /**
   * Closes the specified {@code oldBind} endpoint.
   *
   * @param oldBind the endpoint to close
   */
  public void close(final InetSocketAddress oldBind) {
    Collection<Endpoint> endpoints = this.endpoints.removeAll(oldBind);
    Preconditions.checkState(!endpoints.isEmpty(), "Endpoint was not registered");

    ListenerType type = endpoints.iterator().next().getType();

    // Fire proxy close event to notify plugins of socket close. We block since plugins
    // should have a chance to be notified before the server stops accepting connections.
    server.getEventManager().fire(new ListenerCloseEvent(oldBind, type)).join();

    for (Endpoint endpoint : endpoints) {
      Channel serverChannel = endpoint.getChannel();
      LOGGER.info("Closing endpoint {}", serverChannel.localAddress());
      serverChannel.close().syncUninterruptibly();
    }
  }

  /**
   * Closes all the currently registered endpoints.
   *
   * @param interrupt should closing forward interruptions
   */
  public void closeEndpoints(final boolean interrupt) {
    for (final Map.Entry<InetSocketAddress, Collection<Endpoint>> entry : this.endpoints.asMap().entrySet()) {
      final InetSocketAddress address = entry.getKey();
      final Collection<Endpoint> endpoints = entry.getValue();
      ListenerType type = endpoints.iterator().next().getType();

      // Fire proxy close event to notify plugins of socket close. We block since plugins
      // should have a chance to be notified before the server stops accepting connections.
      server.getEventManager().fire(new ListenerCloseEvent(address, type)).join();

      for (Endpoint endpoint : endpoints) {
        LOGGER.info("Closing endpoint {}", address);
        if (interrupt) {
          try {
            endpoint.getChannel().close().sync();
          } catch (final InterruptedException e) {
            LOGGER.info("Interrupted whilst closing endpoint", e);
            Thread.currentThread().interrupt();
          }
        } else {
          endpoint.getChannel().close().syncUninterruptibly();
        }
      }
    }

    this.endpoints.clear();
  }

  /**
   * Closes all endpoints.
   */
  public void shutdown() {
    this.closeEndpoints(true);

    this.resolver.shutdown();
  }

  /**
   * Returns the Netty boss event loop group used for accepting incoming connections.
   *
   * @return the boss {@link EventLoopGroup}
   */
  public EventLoopGroup getBossGroup() {
    return bossGroup;
  }

  /**
   * Returns the {@link ServerChannelInitializerHolder} currently used to initialize
   * inbound Minecraft server connections.
   *
   * @return the server channel initializer holder
   */
  public ServerChannelInitializerHolder getServerChannelInitializer() {
    return this.serverChannelInitializer;
  }

  /**
   * Returns an HTTP client instance.
   *
   * @return an HTTP client instance.
   */
  public HttpClient createHttpClient() {
    return HttpClient.newBuilder()
        .executor(this.workerGroup)
        .build();
  }

  /**
   * Returns the {@link BackendChannelInitializerHolder} currently used to initialize
   * outbound backend server connections.
   *
   * @return the backend channel initializer holder
   */
  public BackendChannelInitializerHolder getBackendChannelInitializer() {
    return this.backendChannelInitializer;
  }
}
