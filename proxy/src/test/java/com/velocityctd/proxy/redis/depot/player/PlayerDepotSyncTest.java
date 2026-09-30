/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.redis.depot.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.depot.Depot;
import com.velocityctd.proxy.redis.provider.LettuceProvider;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerDepotSyncTest {

  static final class FakeDepot implements Depot<UUID, PlayerEntry> {
    final Map<UUID, PlayerEntry> map = new LinkedHashMap<>();
    int containsCalls;
    int valuesCalls;
    int upsertCalls;

    @Override
    public boolean contains(UUID key) {
      containsCalls++;
      return map.containsKey(key);
    }

    @Override
    public PlayerEntry get(UUID key) {
      return map.get(key);
    }

    @Override
    public void upsert(PlayerEntry value) {
      upsertCalls++;
      map.put(value.getUniqueId(), value);
      value.setDepot(this);
    }

    @Override
    public PlayerEntry remove(UUID key) {
      return map.remove(key);
    }

    @Override
    public Collection<PlayerEntry> values() {
      valuesCalls++;
      return List.copyOf(map.values());
    }

    @Override
    public Collection<String> keys() {
      return map.keySet().stream().map(String::valueOf).toList();
    }

    @Override
    public int size() {
      return map.size();
    }
  }

  private FakeDepot depot;
  private VelocityServer server;
  private PlayerDepotService service;
  private final List<ConnectedPlayer> online = new ArrayList<>();

  private ConnectedPlayer player(String name, String serverName, boolean fully) {
    ConnectedPlayer p = mock(ConnectedPlayer.class);
    UUID id = UUID.nameUUIDFromBytes(name.getBytes());
    when(p.getUniqueId()).thenReturn(id);
    when(p.getUsername()).thenReturn(name);
    when(p.getQueuePriorities()).thenReturn(Map.of());
    when(p.isFullyConnected()).thenReturn(fully);
    if (serverName == null) {
      when(p.getCurrentServer()).thenReturn(Optional.empty());
    } else {
      ServerInfo info = new ServerInfo(serverName, new InetSocketAddress("127.0.0.1", 1));
      VelocityServerConnection conn = mock(VelocityServerConnection.class);
      when(conn.getServerInfo()).thenReturn(info);
      when(p.getCurrentServer()).thenReturn(Optional.of(conn));
    }
    when(p.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 2));
    PlayerSettings settings = mock(PlayerSettings.class);
    when(p.getPlayerSettings()).thenReturn(settings);
    return p;
  }

  private void store(ConnectedPlayer p, String proxyId) {
    PlayerEntry e = new PlayerEntry(p, proxyId);
    e.setDepot(depot);
    depot.map.put(e.getUniqueId(), e);
  }

  private void sync() throws Exception {
    Method m = PlayerDepotService.class.getDeclaredMethod("syncPlayerEntries");
    m.setAccessible(true);
    m.invoke(service);
  }

  @BeforeEach
  void setUp() {
    depot = new FakeDepot();
    LettuceProvider provider = mock(LettuceProvider.class);
    when(provider.<UUID, PlayerEntry>createDepot(PlayerEntry.class)).thenReturn(depot);
    server = mock(VelocityServer.class, RETURNS_DEEP_STUBS);
    when(server.getOnlinePlayers()).thenReturn(online);
    when(server.getPlayer(any(UUID.class))).thenAnswer(inv -> online.stream()
        .filter(p -> p.getUniqueId().equals(inv.getArgument(0))).findFirst());
    VelocityRedis redis = mock(VelocityRedis.class);
    when(redis.getProvider()).thenReturn(provider);
    when(redis.getServer()).thenReturn(server);
    when(redis.getProxyId()).thenReturn("proxy-a");
    when(redis.isShutdown()).thenReturn(false);
    service = new PlayerDepotService(redis);
  }

  @Test
  void countsServersIgnoringCaseFromOneRead() throws Exception {
    store(player("a", "Lobby", true), "proxy-b");
    store(player("b", "lobby", true), "proxy-b");
    store(player("c", "Survival", true), "proxy-b");
    store(player("d", null, true), "proxy-b");
    assertEquals(0, service.getPlayerCountInServer("lobby"), "nothing before the first sync");
    sync();
    assertEquals(2, service.getPlayerCountInServer("LOBBY"));
    assertEquals(1, service.getPlayerCountInServer("survival"));
    assertEquals(0, service.getPlayerCountInServer("missing"));
    assertEquals(1, depot.valuesCalls);
    assertEquals(0, depot.containsCalls, "no per-player round trips");
  }

  @Test
  void upsertsOnlyMissingFullyConnectedLocalPlayers() throws Exception {
    ConnectedPlayer stored = player("stored", "Lobby", true);
    ConnectedPlayer missing = player("missing", "Lobby", true);
    ConnectedPlayer joining = player("joining", "Lobby", false);
    online.addAll(List.of(stored, missing, joining));
    store(stored, "proxy-a");
    sync();
    assertTrue(depot.map.containsKey(missing.getUniqueId()));
    assertFalse(depot.map.containsKey(joining.getUniqueId()));
    assertEquals(2, depot.map.size());
    assertEquals(0, depot.containsCalls, "membership answered from the one read");
  }

  @Test
  void removesOnlyThisProxysStaleEntries() throws Exception {
    ConnectedPlayer here = player("here", "Lobby", true);
    online.add(here);
    store(here, "proxy-a");
    ConnectedPlayer gone = player("gone", "Lobby", true);
    store(gone, "proxy-a");
    ConnectedPlayer elsewhere = player("elsewhere", "Lobby", true);
    store(elsewhere, "proxy-b");
    sync();
    assertTrue(depot.map.containsKey(here.getUniqueId()));
    assertFalse(depot.map.containsKey(gone.getUniqueId()));
    assertTrue(depot.map.containsKey(elsewhere.getUniqueId()));
  }

  @Test
  void listingChangeIsWrittenOnTheNextSync() throws Exception {
    ConnectedPlayer here = player("here", "Lobby", true);
    online.add(here);
    when(here.getPlayerSettings().isClientListingAllowed()).thenReturn(true);
    store(here, "proxy-a");
    when(here.getPlayerSettings().isClientListingAllowed()).thenReturn(false);
    sync();
    assertEquals(1, depot.upsertCalls);
    assertFalse(depot.map.get(here.getUniqueId()).isClientListingAllowed());
  }

  @Test
  void unchangedListingIsNotWrittenAgain() throws Exception {
    ConnectedPlayer here = player("here", "Lobby", true);
    online.add(here);
    when(here.getPlayerSettings().isClientListingAllowed()).thenReturn(true);
    store(here, "proxy-a");
    sync();
    sync();
    assertEquals(0, depot.upsertCalls);
  }

  @Test
  void listingOnAnotherProxysEntryIsLeftAlone() throws Exception {
    ConnectedPlayer here = player("here", "Lobby", true);
    online.add(here);
    when(here.getPlayerSettings().isClientListingAllowed()).thenReturn(true);
    store(here, "proxy-b");
    when(here.getPlayerSettings().isClientListingAllowed()).thenReturn(false);
    sync();
    assertEquals(0, depot.upsertCalls);
    assertTrue(depot.map.get(here.getUniqueId()).isClientListingAllowed());
  }

  @Test
  void syncedEntriesAreReadWithoutAskingRedis() throws Exception {
    store(player("a", "Lobby", true), "proxy-b");
    store(player("b", "Survival", true), "proxy-b");
    assertTrue(service.getSyncedPlayerEntries().isEmpty(), "nothing before the first sync");
    sync();
    int reads = depot.valuesCalls;
    assertEquals(2, service.getSyncedPlayerEntries().size());
    assertEquals(2, service.getSyncedPlayerEntries().size());
    assertEquals(reads, depot.valuesCalls);
  }
}
