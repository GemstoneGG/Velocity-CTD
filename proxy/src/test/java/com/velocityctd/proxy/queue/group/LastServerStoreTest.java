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

package com.velocityctd.proxy.queue.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocityctd.api.queue.LastServerStore;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.provider.LettuceProvider;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for group-scoped last-server persistence and reconciliation.
 */
class LastServerStoreTest {

  private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final long EXPIRY_SECONDS = 2_592_000L;

  private InMemoryLastServerStore memory;
  private LettuceProvider provider;
  private RedisLastServerStore redis;

  @BeforeEach
  void setUp() {
    memory = new InMemoryLastServerStore();
    provider = mock(LettuceProvider.class);
    when(provider.getNamespace()).thenReturn("velocityctd");
    when(provider.isConnected()).thenReturn(true);
    VelocityRedis velocityRedis = mock(VelocityRedis.class);
    when(velocityRedis.isShutdown()).thenReturn(false);
    when(velocityRedis.getProvider()).thenReturn(provider);
    redis = new RedisLastServerStore(velocityRedis);
  }

  @Test
  void memoryStoreKeepsGroupScopesIndependent() {
    memory.recordLastServer(PLAYER, "alpha", "Alpha-01");
    memory.recordLastServer(PLAYER, "beta", "Beta-02");
    memory.recordLastServer(PLAYER, "Legacy-01");

    assertEquals(Optional.of("Alpha-01"), memory.getLastServer(PLAYER, "alpha"));
    assertEquals(Optional.of("Beta-02"), memory.getLastServer(PLAYER, "beta"));
    assertEquals(Optional.of("Legacy-01"), memory.getLastServer(PLAYER));
    assertTrue(memory.getLastServer(PLAYER, "unknown").isEmpty());
  }

  @Test
  void memoryStoreTreatsNullGroupNameAsUnscoped() {
    memory.recordLastServer(PLAYER, null, "Legacy-01");

    assertEquals(Optional.of("Legacy-01"), memory.getLastServer(PLAYER));
    assertEquals(Optional.of("Legacy-01"), memory.getLastServer(PLAYER, null));
  }

  @Test
  void memoryStoreRemovesValuesForEmptyBackendNames() {
    memory.recordLastServer(PLAYER, "alpha", "Alpha-01");
    memory.recordLastServer(PLAYER, "alpha", "");

    assertTrue(memory.getLastServer(PLAYER, "alpha").isEmpty());
  }

  @Test
  void memoryStorePrunesAndIgnoresExpiredEntries() throws Exception {
    Object key = construct("InMemoryLastServerStore$StoreKey",
        new Class<?>[] {UUID.class, String.class}, PLAYER, "alpha");
    Object expired = construct("InMemoryLastServerStore$Entry",
        new Class<?>[] {String.class, long.class}, "Alpha-01", System.currentTimeMillis() - 1L);
    ConcurrentMap<Object, Object> backing = backingMap(memory);
    backing.put(key, expired);
    assertEquals(1, backing.size());

    assertTrue(memory.getLastServer(PLAYER, "alpha").isEmpty());
    assertTrue(backing.isEmpty(), "Expired entries must be pruned on read");

    memory.recordLastServer(PLAYER, "alpha", "Alpha-02");
    assertEquals(Optional.of("Alpha-02"), memory.getLastServer(PLAYER, "alpha"));
  }

  @Test
  void memoryStoreDoesNotShareHistoryBetweenPlayers() {
    UUID other = UUID.randomUUID();
    memory.recordLastServer(PLAYER, "alpha", "Alpha-01");

    assertTrue(memory.getLastServer(other, "alpha").isEmpty());
  }

  @Test
  void redisStoreUsesGroupScopedKeysAndExpiry() {
    redis.recordLastServer(PLAYER, "alpha", "Alpha-01");

    verify(provider).setWithExpiry(
        "velocityctd:queue:last-server:alpha:" + PLAYER, "Alpha-01", EXPIRY_SECONDS);
  }

  @Test
  void redisStoreKeepsLegacyKeysUnscoped() {
    redis.recordLastServer(PLAYER, "Legacy-01");

    verify(provider).setWithExpiry(
        "velocityctd:queue:last-server:" + PLAYER, "Legacy-01", EXPIRY_SECONDS);
  }

  @Test
  void redisStoreReadsGroupScopedKeys() {
    when(provider.get("velocityctd:queue:last-server:alpha:" + PLAYER))
        .thenReturn("Alpha-01");

    assertEquals(Optional.of("Alpha-01"), redis.getLastServer(PLAYER, "alpha"));
    assertTrue(redis.getLastServer(PLAYER, "beta").isEmpty());
  }

  @Test
  void redisStoreToleratesDisconnectedProvider() {
    when(provider.isConnected()).thenReturn(false);

    assertTrue(redis.getLastServer(PLAYER, "alpha").isEmpty());
    redis.recordLastServer(PLAYER, "alpha", "Alpha-01");

    verify(provider, never()).setWithExpiry(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void fallbackReconcilesPrimaryValueIntoLocalCopy() {
    LastServerStore primary = mock(LastServerStore.class);
    InMemoryLastServerStore fallback = new InMemoryLastServerStore();
    when(primary.getLastServer(PLAYER, "alpha")).thenReturn(Optional.of("Alpha-01"));
    FallbackLastServerStore store = new FallbackLastServerStore(primary, fallback);

    assertEquals(Optional.of("Alpha-01"), store.getLastServer(PLAYER, "alpha"));
    assertEquals(Optional.of("Alpha-01"), fallback.getLastServer(PLAYER, "alpha"));
  }

  @Test
  void fallbackUsesLocalCopyWhenPrimaryHasNoValue() {
    LastServerStore primary = mock(LastServerStore.class);
    InMemoryLastServerStore fallback = new InMemoryLastServerStore();
    FallbackLastServerStore store = new FallbackLastServerStore(primary, fallback);
    fallback.recordLastServer(PLAYER, "alpha", "Alpha-02");

    assertEquals(Optional.of("Alpha-02"), store.getLastServer(PLAYER, "alpha"));
    verify(primary).getLastServer(PLAYER, "alpha");
  }

  @Test
  void fallbackWritesBothScopesOnRecord() {
    LastServerStore primary = mock(LastServerStore.class);
    LastServerStore fallback = mock(LastServerStore.class);
    FallbackLastServerStore store = new FallbackLastServerStore(primary, fallback);

    store.recordLastServer(PLAYER, "alpha", "Alpha-01");

    verify(primary).recordLastServer(PLAYER, "alpha", "Alpha-01");
    verify(fallback).recordLastServer(PLAYER, "alpha", "Alpha-01");
  }

  @Test
  void createdStoreUsesMemoryOnlyWithoutRedis() {
    LastServerStore store = LastServerStores.create(null);

    store.recordLastServer(PLAYER, "alpha", "Alpha-01");
    assertEquals(Optional.of("Alpha-01"), store.getLastServer(PLAYER, "alpha"));
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMap<Object, Object> backingMap(InMemoryLastServerStore store)
      throws Exception {
    Field field = InMemoryLastServerStore.class.getDeclaredField("lastServers");
    field.setAccessible(true);
    return (ConcurrentMap<Object, Object>) field.get(store);
  }

  private static Object construct(String nestedClass, Class<?>[] types, Object... arguments)
      throws Exception {
    Class<?> definition = Class.forName(
        "com.velocityctd.proxy.queue.group." + nestedClass);
    Constructor<?> constructor = definition.getDeclaredConstructor(types);
    constructor.setAccessible(true);
    return constructor.newInstance(arguments);
  }
}
