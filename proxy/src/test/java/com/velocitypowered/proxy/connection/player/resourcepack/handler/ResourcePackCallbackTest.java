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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent.Status;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.player.resourcepack.ResourcePackResponseBundle;
import com.velocitypowered.proxy.event.VelocityEventManager;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.resource.ResourcePackCallback;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourcePackCallbackTest {

  private final ExecutorService pool = Executors.newFixedThreadPool(4);
  private VelocityServer server;
  private ConnectedPlayer player;
  private ModernResourcePackHandler handler;

  @BeforeEach
  void setUp() {
    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    PluginManager plugins = mock(PluginManager.class);
    PluginContainer container = mock(PluginContainer.class);
    when(container.getExecutorService()).thenReturn(pool);
    when(plugins.ensurePluginContainer(any())).thenReturn(container);
    when(server.getPluginManager()).thenReturn(plugins);
    player = mock(ConnectedPlayer.class, RETURNS_DEEP_STUBS);
    when(player.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_4);
    handler = new ModernResourcePackHandler(player, server);
  }

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
  }

  private UUID queue(ResourcePackCallback callback) {
    UUID id = UUID.randomUUID();
    handler.queueResourcePack(ResourcePackRequest.resourcePackRequest()
        .packs(ResourcePackInfo.resourcePackInfo(id, URI.create("https://example.com/p.zip"),
            "0123456789abcdef0123456789abcdef01234567"))
        .callback(callback)
        .build());
    return id;
  }

  @Test
  void callbacksRunInArrivalOrder() throws Exception {
    for (int run = 0; run < 50; run++) {
      List<ResourcePackStatus> seen = new CopyOnWriteArrayList<>();
      CountDownLatch done = new CountDownLatch(3);
      UUID id = queue((uuid, status, audience) -> {
        try {
          Thread.sleep(ThreadLocalRandom.current().nextInt(3));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        seen.add(status);
        done.countDown();
      });
      handler.dispatchPackCallback(id, Status.ACCEPTED);
      handler.dispatchPackCallback(id, Status.DOWNLOADED);
      handler.dispatchPackCallback(id, Status.SUCCESSFUL);
      assertTrue(done.await(5, TimeUnit.SECONDS));
      assertEquals(List.of(ResourcePackStatus.ACCEPTED, ResourcePackStatus.DOWNLOADED,
          ResourcePackStatus.SUCCESSFULLY_LOADED), seen, "run " + run);
    }
  }

  @Test
  void clearKeepsPendingCallbacksAndTerminalEvicts() throws Exception {
    List<ResourcePackStatus> seen = new CopyOnWriteArrayList<>();
    CountDownLatch done = new CountDownLatch(1);
    UUID id = queue((uuid, status, audience) -> {
      seen.add(status);
      done.countDown();
    });
    handler.clearAppliedResourcePacks();
    handler.dispatchPackCallback(id, Status.DISCARDED);
    assertTrue(done.await(5, TimeUnit.SECONDS), "callback survives a clear");
    handler.dispatchPackCallback(id, Status.SUCCESSFUL);
    Thread.sleep(100);
    assertEquals(List.of(ResourcePackStatus.DISCARDED), seen, "terminal status evicted it");
  }

  @Test
  void eventFiresWithoutWaitingForSlowCallback() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    VelocityEventManager events = mock(VelocityEventManager.class);
    CountDownLatch fired = new CountDownLatch(1);
    when(events.fire(any(PlayerResourcePackStatusEvent.class))).thenAnswer(inv -> {
      fired.countDown();
      return new CompletableFuture<>();
    });
    when(server.getEventManager()).thenReturn(events);
    UUID id = queue((uuid, status, audience) -> {
      try {
        release.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    handler.onResourcePackResponse(new ResourcePackResponseBundle(id, "", Status.ACCEPTED));
    assertTrue(fired.await(2, TimeUnit.SECONDS), "event fired while the callback is still blocked");
    release.countDown();
  }
}
