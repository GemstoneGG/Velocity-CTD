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

package com.velocitypowered.proxy.connection.util;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import com.velocitypowered.proxy.util.except.QuietRuntimeException;
import io.netty.channel.EventLoop;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Pings backends for ping passthrough.
 */
final class BackendPings {

  static final int MAX_PINGS_PER_SECOND =
      Integer.getInteger("velocity.max-backend-pings-per-second", 128);

  private static final Duration ANSWER_LIFETIME = Duration.ofSeconds(1);
  private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
  private static final QuietRuntimeException NO_ANSWER =
      new QuietRuntimeException("no answer from the backend");

  private final Ticker ticker;
  private final AsyncCache<Key, Optional<ServerPing>> answers;
  private final Cache<ServerInfo, Window> windows;

  BackendPings() {
    this(Ticker.systemTicker());
  }

  BackendPings(final Ticker ticker) {
    this.ticker = ticker;
    this.answers = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfterWrite(ANSWER_LIFETIME)
        .buildAsync();
    this.windows = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfterAccess(Duration.ofMinutes(1))
        .build();
  }

  /**
   * Pings a backend on behalf of a server list ping, sharing the answer with every identical ping
   * made within {@link #ANSWER_LIFETIME}.
   *
   * @param server  the backend to ping
   * @param loop    the event loop to open a new backend connection on
   * @param options the protocol version and virtual host to ping with
   * @return the backend's answer, failing when the backend did not answer or its budget for this
   *     second is spent
   */
  CompletableFuture<ServerPing> ping(final VelocityRegisteredServer server,
                                     final @Nullable EventLoop loop, final PingOptions options) {
    final Key key = new Key(server.getServerInfo(), options.getProtocolVersion(),
        options.getVirtualHost());
    CompletableFuture<Optional<ServerPing>> answer = answers.getIfPresent(key);
    if (answer == null) {
      // A ping refused here is never cached, so a flood of new virtual hosts fills nothing.
      if (!admits(key.server())) {
        return CompletableFuture.failedFuture(NO_ANSWER);
      }
      answer = answers.get(key, (ignored, executor) -> server.ping(loop, options)
          .handle((ping, error) -> Optional.ofNullable(error == null ? ping : null)));
    }
    return answer.thenCompose(ping -> ping
        .map(CompletableFuture::completedFuture)
        .orElseGet(() -> CompletableFuture.failedFuture(NO_ANSWER)));
  }

  private boolean admits(final ServerInfo server) {
    final long second = ticker.read() / NANOS_PER_SECOND;
    final Window window = windows.asMap().compute(server, (info, current) ->
        current == null || current.second() != second
            ? new Window(second, 1) : new Window(second, current.pings() + 1));
    return window.pings() <= MAX_PINGS_PER_SECOND;
  }

  private record Key(ServerInfo server, ProtocolVersion version, @Nullable String virtualHost) {
  }

  private record Window(long second, int pings) {
  }
}
