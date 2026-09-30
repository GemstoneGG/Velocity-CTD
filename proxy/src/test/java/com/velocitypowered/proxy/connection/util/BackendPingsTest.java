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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/**
 * A flood of server list pings with passthrough on stays a bounded number of backend pings.
 */
class BackendPingsTest {

  private final AtomicLong nanos = new AtomicLong();
  private final Ticker ticker = nanos::get;
  private final BackendPings pings = new BackendPings(ticker);

  private static PingOptions options(final String virtualHost) {
    return PingOptions.builder()
        .version(ProtocolVersion.MINECRAFT_1_21_4)
        .virtualHost(virtualHost)
        .build();
  }

  private static ServerPing answer(final String name) {
    return new ServerPing(new ServerPing.Version(769, name), null, Component.text(name), null);
  }

  private static VelocityRegisteredServer backend(final String name,
                                                  final List<CompletableFuture<ServerPing>> sent) {
    final VelocityRegisteredServer server = mock(VelocityRegisteredServer.class);
    when(server.getServerInfo())
        .thenReturn(new ServerInfo(name, InetSocketAddress.createUnresolved(name, 25565)));
    when(server.ping(any(), any(PingOptions.class))).thenAnswer(invocation -> {
      final CompletableFuture<ServerPing> ping = new CompletableFuture<>();
      sent.add(ping);
      return ping;
    });
    return server;
  }

  private void advanceMillis(final long millis) {
    nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
  }

  @Test
  void identicalPingsShareOneBackendPing() {
    final List<CompletableFuture<ServerPing>> sent = new CopyOnWriteArrayList<>();
    final VelocityRegisteredServer lobby = backend("lobby", sent);
    final List<CompletableFuture<ServerPing>> answered = new ArrayList<>();
    for (int i = 0; i < 1_000; i++) {
      answered.add(pings.ping(lobby, null, options("play.example.com")));
    }
    assertEquals(1, sent.size());

    final ServerPing ping = answer("lobby");
    sent.getFirst().complete(ping);
    answered.forEach(future -> assertSame(ping, future.join()));
    advanceMillis(900);
    assertSame(ping, pings.ping(lobby, null, options("play.example.com")).join());
    assertEquals(1, sent.size());

    advanceMillis(200);
    pings.ping(lobby, null, options("play.example.com"));
    assertEquals(2, sent.size(), "a new ping once the answer is a second old");
  }

  @Test
  void eachVirtualHostGetsItsOwnAnswer() {
    final List<CompletableFuture<ServerPing>> sent = new CopyOnWriteArrayList<>();
    final VelocityRegisteredServer lobby = backend("lobby", sent);
    final CompletableFuture<ServerPing> first = pings.ping(lobby, null, options("a.example.com"));
    final CompletableFuture<ServerPing> second = pings.ping(lobby, null, options("b.example.com"));
    assertEquals(2, sent.size());

    final ServerPing forFirst = answer("a");
    final ServerPing forSecond = answer("b");
    sent.get(0).complete(forFirst);
    sent.get(1).complete(forSecond);
    assertSame(forFirst, first.join());
    assertSame(forSecond, second.join());
  }

  @Test
  void floodOfNewVirtualHostsIsCappedPerServer() {
    final List<CompletableFuture<ServerPing>> sentToLobby = new CopyOnWriteArrayList<>();
    final List<CompletableFuture<ServerPing>> sentToHub = new CopyOnWriteArrayList<>();
    final VelocityRegisteredServer lobby = backend("lobby", sentToLobby);
    final VelocityRegisteredServer hub = backend("hub", sentToHub);
    int refused = 0;
    for (int i = 0; i < BackendPings.MAX_PINGS_PER_SECOND + 1_000; i++) {
      if (pings.ping(lobby, null, options(i + ".example.com")).isCompletedExceptionally()) {
        refused++;
      }
    }
    assertEquals(BackendPings.MAX_PINGS_PER_SECOND, sentToLobby.size());
    assertEquals(1_000, refused);

    pings.ping(hub, null, options("play.example.com"));
    assertEquals(1, sentToHub.size(), "each server has its own budget");

    advanceMillis(1_000);
    pings.ping(lobby, null, options("next.example.com"));
    assertEquals(BackendPings.MAX_PINGS_PER_SECOND + 1, sentToLobby.size());
  }

  @Test
  void refusedPingIsNotRemembered() {
    final List<CompletableFuture<ServerPing>> sent = new CopyOnWriteArrayList<>();
    final VelocityRegisteredServer lobby = backend("lobby", sent);
    for (int i = 0; i < BackendPings.MAX_PINGS_PER_SECOND; i++) {
      pings.ping(lobby, null, options(i + ".example.com"));
    }
    assertTrue(pings.ping(lobby, null, options("play.example.com")).isCompletedExceptionally());

    advanceMillis(1_000);
    final CompletableFuture<ServerPing> retried =
        pings.ping(lobby, null, options("play.example.com"));
    assertEquals(BackendPings.MAX_PINGS_PER_SECOND + 1, sent.size());
    final ServerPing ping = answer("lobby");
    sent.getLast().complete(ping);
    assertSame(ping, retried.join());
  }

  @Test
  void unansweredPingIsSharedUntilItExpires() {
    final List<CompletableFuture<ServerPing>> sent = new CopyOnWriteArrayList<>();
    final VelocityRegisteredServer lobby = backend("lobby", sent);
    final CompletableFuture<ServerPing> first =
        pings.ping(lobby, null, options("play.example.com"));
    sent.getFirst().completeExceptionally(new SocketException("Connection refused"));
    assertTrue(first.isCompletedExceptionally());
    for (int i = 0; i < 1_000; i++) {
      assertTrue(pings.ping(lobby, null, options("play.example.com")).isCompletedExceptionally());
    }
    assertEquals(1, sent.size(), "a backend that is down is not tried again by every ping");

    advanceMillis(1_100);
    pings.ping(lobby, null, options("play.example.com"));
    assertEquals(2, sent.size());
  }
}
