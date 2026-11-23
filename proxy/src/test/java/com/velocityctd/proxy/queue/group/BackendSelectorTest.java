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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocityctd.api.queue.SelectionStrategy;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackendSelectorTest {

  private static final List<String> BACKENDS = List.of("Survival-01", "Survival-02", "Survival-03");

  private static List<String> available(List<String> configured, Map<String, Integer> counts, int maxPlayers) {
    return BackendSelector.availableBackends(
        configured, name -> true, name -> counts.getOrDefault(name, 0), maxPlayers, null);
  }

  @Test
  void leastPlayersPicksBackendWithLowestCount() {
    Map<String, Integer> counts = Map.of("Survival-01", 12, "Survival-02", 4, "Survival-03", 9);
    String chosen = BackendSelector.select(SelectionStrategy.LEAST_PLAYERS, BACKENDS,
        name -> counts.get(name), null, new AtomicInteger(), new Random());
    assertEquals("Survival-02", chosen);
  }

  @Test
  void leastPlayersBreaksTiesByConfiguredOrder() {
    Map<String, Integer> counts = Map.of("Survival-01", 5, "Survival-02", 5, "Survival-03", 5);
    String chosen = BackendSelector.select(SelectionStrategy.LEAST_PLAYERS, BACKENDS,
        name -> counts.get(name), null, new AtomicInteger(), new Random());
    assertEquals("Survival-01", chosen);
  }

  @Test
  void lastServerReturnsRememberedBackendWhenAvailable() {
    String chosen = BackendSelector.select(SelectionStrategy.LAST_SERVER, BACKENDS,
        name -> 50, "Survival-03", new AtomicInteger(), new Random());
    assertEquals("Survival-03", chosen);
  }

  @Test
  void lastServerFallsBackToLeastPlayersWhenRememberedBackendIsUnavailable() {
    Map<String, Integer> counts = Map.of("Survival-01", 7, "Survival-02", 2, "Survival-03", 1);
    String chosen = BackendSelector.select(SelectionStrategy.LAST_SERVER, BACKENDS,
        name -> counts.get(name), "Other-01", new AtomicInteger(), new Random());
    assertEquals("Survival-03", chosen);
  }

  @Test
  void roundRobinRotatesAcrossSuccessiveCalls() {
    AtomicInteger counter = new AtomicInteger();
    assertEquals("Survival-01", BackendSelector.select(SelectionStrategy.ROUND_ROBIN, BACKENDS,
        name -> 0, null, counter, new Random()));
    assertEquals("Survival-02", BackendSelector.select(SelectionStrategy.ROUND_ROBIN, BACKENDS,
        name -> 0, null, counter, new Random()));
    assertEquals("Survival-03", BackendSelector.select(SelectionStrategy.ROUND_ROBIN, BACKENDS,
        name -> 0, null, counter, new Random()));
    assertEquals("Survival-01", BackendSelector.select(SelectionStrategy.ROUND_ROBIN, BACKENDS,
        name -> 0, null, counter, new Random()));
  }

  @Test
  void roundRobinWrapsWhenBackendDropsOut() {
    AtomicInteger counter = new AtomicInteger(1);
    List<String> reduced = List.of("Survival-01", "Survival-03");
    assertEquals("Survival-03", BackendSelector.select(SelectionStrategy.ROUND_ROBIN, reduced,
        name -> 0, null, counter, new Random()));
    assertEquals("Survival-01", BackendSelector.select(SelectionStrategy.ROUND_ROBIN, reduced,
        name -> 0, null, counter, new Random()));
  }

  @Test
  void randomAlwaysReturnsAMemberOfTheAvailableSet() {
    Set<String> allowed = Set.copyOf(BACKENDS);
    Random random = new Random(1234L);
    for (int index = 0; index < 100; index++) {
      String chosen = BackendSelector.select(SelectionStrategy.RANDOM, BACKENDS,
          name -> 0, null, new AtomicInteger(), random);
      assertTrue(allowed.contains(chosen));
    }
  }

  @Test
  void emptyAvailableThrowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> BackendSelector.select(
        SelectionStrategy.LEAST_PLAYERS, List.of(), name -> 0, null,
        new AtomicInteger(), new Random()));
  }

  @Test
  void removesDuplicateNamesAndKeepsConfiguredOrder() {
    List<String> configured = List.of("Lobby-01", "Lobby-02", "Lobby-01", "Lobby-03", "Lobby-02");
    assertEquals(List.of("Lobby-01", "Lobby-02", "Lobby-03"),
        available(configured, Map.of(), -1));
  }

  @Test
  void filtersOutFullBackendsAndHonoursZeroLimit() {
    Map<String, Integer> counts = Map.of("Survival-01", 30, "Survival-02", 5, "Survival-03", 0);
    assertEquals(List.of("Survival-02", "Survival-03"), available(BACKENDS, counts, 10));
    // A backend at exactly the limit is full and is excluded as well.
    Map<String, Integer> exact = Map.of("Survival-01", 10, "Survival-02", 5, "Survival-03", 0);
    assertEquals(List.of("Survival-02", "Survival-03"), available(BACKENDS, exact, 10));
    assertEquals(BACKENDS, available(BACKENDS, counts, 0));
    assertFalse(available(List.of("Survival-01"), counts, 10).contains("Survival-01"));
  }
}
