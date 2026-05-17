/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.redis;

import com.velocityctd.proxy.queue.redis.depot.VelocityQueueDepotService;
import com.velocityctd.proxy.redis.depot.player.PlayerDepotService;
import com.velocityctd.proxy.redis.depot.proxy.ProxyDepotService;
import com.velocityctd.proxy.redis.handler.RouteHandlerRegistry;
import com.velocityctd.proxy.redis.handler.TransactionHandlerRegistry;
import com.velocityctd.proxy.redis.packet.PacketSerializer;
import com.velocityctd.proxy.redis.provider.AbstractRedisProvider;
import com.velocityctd.proxy.redis.provider.LettuceProvider;
import com.velocityctd.proxy.redis.provider.RedisProvider;
import com.velocityctd.proxy.redis.transaction.Transaction;
import com.velocityctd.proxy.redis.transaction.TransactionData;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the central Redis integration manager used by the Velocity proxy.
 *
 * <p>This class initializes and manages the Redis provider, depot services, route
 * registrations, transaction handlers, and proxy identity information used for
 * inter-proxy communication.</p>
 */
public final class VelocityRedis {

  /**
   * The {@link VelocityServer} instance that owns this Redis module.
   */
  private final VelocityServer server;

  /**
   * The {@link RedisProvider} responsible for Redis communication and pub/sub.
   */
  private final RedisProvider provider;

  /**
   * The service responsible for tracking player-related Redis entries.
   */
  private final PlayerDepotService playerService;

  /**
   * The service responsible for managing proxy-related Redis entries.
   */
  private final ProxyDepotService proxyService;

  /**
   * The service responsible for managing queue-related Redis entries.
   */
  private final VelocityQueueDepotService queueService;

  /**
   * The proxy identifier for this server instance, used for inter-proxy coordination.
   */
  private final String proxyId;

  /**
   * Whether the Redis module has been shutdown.
   */
  private boolean shutdown = false;

  /**
   * Constructs a new {@link VelocityRedis} instance.
   *
   * @param server the {@link VelocityServer} instance
   */
  public VelocityRedis(@NotNull VelocityServer server) {
    VelocityConfiguration.Redis config = server.getConfiguration().getRedis();
    this.proxyId = config.getProxyId();

    this.server = server;
    this.provider = new LettuceProvider(config, server.getScheduler(), new PacketSerializer());
    this.provider.restart();

    this.playerService = new PlayerDepotService(this);
    this.proxyService = new ProxyDepotService(this);
    this.queueService = new VelocityQueueDepotService(this);

    this.registerRoutes();
    this.registerTransactionHandlers();
  }

  /**
   * Shuts down the Redis service and all associated depot services.
   *
   * <p>This method ensures that all resources, connections, and cached entries
   * are properly cleaned up before termination.</p>
   */
  public void shutdown() {
    if (shutdown) {
      return;
    }

    shutdown = true;

    this.playerService.teardown();
    this.proxyService.teardown();
    this.queueService.teardown();
    this.provider.disconnect();
  }

  /**
   * Registers all route registrations defined in {@link RouteHandlerRegistry} with the current
   * {@link RedisProvider} instance.
   *
   * <p>Each route represents a "one-way" packet handler for incoming Redis packets.</p>
   */
  private void registerRoutes() {
    for (RouteHandlerRegistry registry : RouteHandlerRegistry.values()) {
      this.provider.registerRoute(registry.createRouteHandler(this.server));
    }
  }

  /**
   * Registers all transaction handlers defined in {@link TransactionHandlerRegistry} with the current
   * {@link RedisProvider} instance.
   *
   * <p>Each transaction handler represents logic for request/response packet pairs.</p>
   */
  private void registerTransactionHandlers() {
    for (TransactionHandlerRegistry registry : TransactionHandlerRegistry.values()) {
      this.provider.registerTransaction(registry.createTransactionHandler(this.server));
    }
  }

  /**
   * Gets the {@link VelocityServer} instance associated with this Redis provider.
   *
   * @return the server instance
   * @throws IllegalStateException if the server reference is unexpectedly null
   */
  public VelocityServer getServer() {
    if (server == null) {
      throw new IllegalStateException("You're trying to access the server of redis before it was initialized!");
    }

    return server;
  }

  /**
   * Gets the {@link RedisProvider} instance for Redis communication.
   *
   * @return the redis provider
   */
  public RedisProvider getProvider() {
    return provider;
  }

  /**
   * Gets the {@link PlayerDepotService} associated with this Redis module.
   *
   * @return the player service
   */
  public PlayerDepotService getPlayerService() {
    return playerService;
  }

  /**
   * Gets the {@link ProxyDepotService} associated with this Redis module.
   *
   * @return the proxy service
   */
  public ProxyDepotService getProxyService() {
    return proxyService;
  }

  /**
   * Gets the {@link VelocityQueueDepotService} associated with this Redis module.
   *
   * @return the queue service
   */
  public VelocityQueueDepotService getQueueService() {
    return queueService;
  }

  /**
   * Publishes a one-way payload to all subscribers on the Redis channel.
   *
   * @param payload the payload to publish
   */
  public <T> void publish(@NotNull T payload) {
    provider.publish(payload);
  }

  /**
   * Publishes a transaction to all subscribers on the Redis channel and returns
   * a future that completes with the response data.
   *
   * @param data the transaction data to send
   * @param <T> the type of the data
   * @param <R> the type of the expected response
   * @return a future that completes with the response data or exceptionally on timeout
   */
  public <T extends TransactionData<R>, R> CompletableFuture<R> publishTransaction(@NotNull T data) {
    Transaction<T, R> transaction = Transaction.of(data);
    provider.publish(transaction);
    return transaction.getFuture();
  }

  /**
   * Gets the proxy identifier for this server instance.
   *
   * @return the proxy identifier
   */
  public String getProxyId() {
    return proxyId;
  }

  /**
   * Registers a listener which is invoked whenever the Redis pub/sub connection is
   * re-established after a disconnection.
   *
   * @param listener the callback to register
   */
  public void addReconnectListener(@NotNull Runnable listener) {
    ((AbstractRedisProvider) provider).addReconnectListener(listener);
  }

  /**
   * Determines whether this Redis module has been shut down.
   *
   * @return {@code true} if the Redis service has been shut down, otherwise {@code false}
   */
  public boolean isShutdown() {
    return shutdown;
  }
}
