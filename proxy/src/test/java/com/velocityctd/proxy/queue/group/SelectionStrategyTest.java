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

import com.velocityctd.api.queue.SelectionStrategy;
import org.junit.jupiter.api.Test;

class SelectionStrategyTest {

  @Test
  void parsesAllConfiguredNames() {
    assertEquals(SelectionStrategy.LEAST_PLAYERS, SelectionStrategy.fromConfig("least_players"));
    assertEquals(SelectionStrategy.LAST_SERVER, SelectionStrategy.fromConfig("last_server"));
    assertEquals(SelectionStrategy.ROUND_ROBIN, SelectionStrategy.fromConfig("round_robin"));
    assertEquals(SelectionStrategy.RANDOM, SelectionStrategy.fromConfig("random"));
  }

  @Test
  void acceptsCaseAndHyphenVariants() {
    assertEquals(SelectionStrategy.LEAST_PLAYERS, SelectionStrategy.fromConfig("LEAST-PLAYERS"));
    assertEquals(SelectionStrategy.LAST_SERVER, SelectionStrategy.fromConfig(" Last-Server "));
    assertEquals(SelectionStrategy.ROUND_ROBIN, SelectionStrategy.fromConfig("ROUND-ROBIN"));
    assertEquals(SelectionStrategy.RANDOM, SelectionStrategy.fromConfig("RaNdOm"));
  }

  @Test
  void unknownAndMissingValuesFallBackToLeastPlayers() {
    assertEquals(SelectionStrategy.LEAST_PLAYERS, SelectionStrategy.fromConfig(null));
    assertEquals(SelectionStrategy.LEAST_PLAYERS, SelectionStrategy.fromConfig(""));
    assertEquals(SelectionStrategy.LEAST_PLAYERS, SelectionStrategy.fromConfig("   "));
    assertEquals(SelectionStrategy.LEAST_PLAYERS, SelectionStrategy.fromConfig("something_else"));
  }

  @Test
  void configNamesRoundTrip() {
    for (SelectionStrategy strategy : SelectionStrategy.values()) {
      assertEquals(strategy, SelectionStrategy.fromConfig(strategy.configName()));
    }
  }
}
