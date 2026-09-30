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

package com.velocitypowered.proxy.connection.player.resourcepack.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent.Status;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.player.resourcepack.ResourcePackResponseBundle;
import com.velocitypowered.proxy.connection.player.resourcepack.VelocityResourcePackInfo;
import com.velocitypowered.proxy.event.VelocityEventManager;
import com.velocitypowered.proxy.protocol.packet.ResourcePackResponsePacket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * A client status reaches the status listeners only for a pack the proxy offered, so a client
 * repeating statuses cannot run them once per packet.
 */
class ResourcePackStatusTest {

  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

  private final List<PlayerResourcePackStatusEvent> fired = new CopyOnWriteArrayList<>();
  private final MinecraftConnection backend = mock(MinecraftConnection.class);

  private ResourcePackHandler handlerFor(final ProtocolVersion version) {
    final VelocityServer server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    final VelocityEventManager events = mock(VelocityEventManager.class);
    when(events.fire(any(PlayerResourcePackStatusEvent.class))).thenAnswer(invocation -> {
      fired.add(invocation.getArgument(0));
      return new CompletableFuture<>();
    });
    when(server.getEventManager()).thenReturn(events);
    final ConnectedPlayer player = mock(ConnectedPlayer.class, RETURNS_DEEP_STUBS);
    when(player.getProtocolVersion()).thenReturn(version);
    final VelocityServerConnection inFlight = mock(VelocityServerConnection.class);
    when(inFlight.getConnection()).thenReturn(backend);
    when(player.getConnectionInFlight()).thenReturn(inFlight);
    return ResourcePackHandler.create(player, server);
  }

  private static ResourcePackInfo offer(final ResourcePackHandler handler) {
    final ResourcePackInfo pack = new VelocityResourcePackInfo.BuilderImpl(
        "https://example.com/pack.zip")
        .setId(UUID.randomUUID())
        .setOrigin(ResourcePackInfo.Origin.DOWNSTREAM_SERVER)
        .build();
    handler.queueResourcePack(pack);
    return pack;
  }

  private static boolean answer(final ResourcePackHandler handler, final UUID id,
                                final Status status) {
    return handler.onResourcePackResponse(new ResourcePackResponseBundle(id, HASH, status));
  }

  @Test
  void statusForPackNeverOfferedRunsNothing() {
    final ResourcePackHandler handler = handlerFor(ProtocolVersion.MINECRAFT_1_21_4);
    for (int i = 0; i < 1_000; i++) {
      for (final Status status : Status.values()) {
        assertTrue(answer(handler, UUID.randomUUID(), status), status.name());
      }
    }
    assertEquals(List.of(), fired);
    verify(backend, never()).write(any());
  }

  @Test
  void offeredPackReportsEveryStatus() {
    final ResourcePackHandler handler = handlerFor(ProtocolVersion.MINECRAFT_1_21_4);
    final ResourcePackInfo pack = offer(handler);
    assertFalse(answer(handler, pack.getId(), Status.ACCEPTED));
    assertFalse(answer(handler, pack.getId(), Status.DOWNLOADED));
    assertFalse(answer(handler, pack.getId(), Status.SUCCESSFUL));
    assertEquals(List.of(Status.ACCEPTED, Status.DOWNLOADED, Status.SUCCESSFUL),
        fired.stream().map(PlayerResourcePackStatusEvent::getStatus).toList());
    fired.forEach(event -> assertSame(pack, event.getPackInfo()));
    assertEquals(List.of(pack), List.copyOf(handler.getAppliedResourcePacks()));
    verify(backend, times(3)).write(any(ResourcePackResponsePacket.class));
  }

  @Test
  void repeatedSuccessForAppliedPackReachesBackendWithoutEvent() {
    final ResourcePackHandler handler = handlerFor(ProtocolVersion.MINECRAFT_1_21_4);
    final ResourcePackInfo pack = offer(handler);
    answer(handler, pack.getId(), Status.ACCEPTED);
    answer(handler, pack.getId(), Status.SUCCESSFUL);
    for (int i = 0; i < 1_000; i++) {
      assertFalse(answer(handler, pack.getId(), Status.SUCCESSFUL));
    }
    assertEquals(2, fired.size());
    assertEquals(List.of(pack), List.copyOf(handler.getAppliedResourcePacks()));
    verify(backend, times(1_002)).write(any(ResourcePackResponsePacket.class));
  }

  @Test
  void progressForAppliedPackWithNoOfferIsDropped() {
    final ResourcePackHandler handler = handlerFor(ProtocolVersion.MINECRAFT_1_21_4);
    final ResourcePackInfo pack = offer(handler);
    answer(handler, pack.getId(), Status.ACCEPTED);
    answer(handler, pack.getId(), Status.SUCCESSFUL);
    assertTrue(answer(handler, pack.getId(), Status.ACCEPTED));
    assertTrue(answer(handler, pack.getId(), Status.DOWNLOADED));
    assertEquals(2, fired.size());
    verify(backend, times(2)).write(any(ResourcePackResponsePacket.class));
  }

  @Test
  void failureForAppliedPackIsReportedOnce() {
    final ResourcePackHandler handler = handlerFor(ProtocolVersion.MINECRAFT_1_21_4);
    final ResourcePackInfo pack = offer(handler);
    answer(handler, pack.getId(), Status.ACCEPTED);
    answer(handler, pack.getId(), Status.SUCCESSFUL);
    assertFalse(answer(handler, pack.getId(), Status.DISCARDED));
    assertTrue(answer(handler, pack.getId(), Status.DISCARDED));
    assertEquals(List.of(Status.ACCEPTED, Status.SUCCESSFUL, Status.DISCARDED),
        fired.stream().map(PlayerResourcePackStatusEvent::getStatus).toList());
    assertTrue(handler.getAppliedResourcePacks().isEmpty());
  }

  @Test
  void legacyStatusWithNothingOutstandingRunsNothing() {
    for (final ProtocolVersion version
        : List.of(ProtocolVersion.MINECRAFT_1_16_4, ProtocolVersion.MINECRAFT_1_20_2)) {
      final ResourcePackHandler handler = handlerFor(version);
      for (int i = 0; i < 1_000; i++) {
        for (final Status status : List.of(Status.ACCEPTED, Status.SUCCESSFUL,
            Status.DECLINED, Status.FAILED_DOWNLOAD)) {
          assertTrue(answer(handler, null, status), version + " " + status);
        }
      }
    }
    assertEquals(List.of(), fired);
    verify(backend, never()).write(any());
  }

  @Test
  void legacyStraySuccessKeepsTheAppliedPack() {
    final ResourcePackHandler handler = handlerFor(ProtocolVersion.MINECRAFT_1_20_2);
    final ResourcePackInfo pack = offer(handler);
    assertFalse(answer(handler, null, Status.ACCEPTED));
    assertFalse(answer(handler, null, Status.SUCCESSFUL));
    assertTrue(answer(handler, null, Status.SUCCESSFUL));
    assertEquals(2, fired.size());
    final ResourcePackInfo applied = handler.getFirstAppliedPack();
    assertNotNull(applied);
    assertSame(pack, applied);
  }
}
