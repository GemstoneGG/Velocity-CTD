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

import com.velocityctd.api.queue.SelectionStrategy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Pure helpers for filtering and selecting a backend from a server group.
 *
 * <p>These methods keep no state of their own so the selection rules can be tested
 * in isolation. Callers own the round-robin counter and the random number generator.</p>
 */
public final class BackendSelector {

  private BackendSelector() {
    throw new AssertionError();
  }

  /**
   * Filters configured backends down to those that are online and below the group limit.
   *
   * <p>Configured order is preserved and duplicate names are ignored. A backend with a
   * player count at or above {@code maxPlayers} is excluded. A non-positive limit means
   * the group applies no limit of its own.</p>
   *
   * @param configured   the configured backend names, in selection order
   * @param isOnline     tests whether a backend is online
   * @param playerCount  returns the current player count of a backend
   * @param maxPlayers   the group player limit, or a non-positive value for no limit
   * @param lastServer   the player's last backend, retained for callers that need it
   * @return an eligible backend snapshot in configured order
   */
  public static List<String> availableBackends(List<String> configured,
                                               Predicate<String> isOnline,
                                               ToIntFunction<String> playerCount,
                                               int maxPlayers,
                                               @Nullable String lastServer) {
    Set<String> seen = new HashSet<>();
    List<String> available = new ArrayList<>();

    for (String name : configured) {
      if (name == null || name.isEmpty() || !seen.add(name)) {
        continue;
      }

      if (!isOnline.test(name)) {
        continue;
      }

      if (maxPlayers > 0 && playerCount.applyAsInt(name) >= maxPlayers) {
        continue;
      }

      available.add(name);
    }

    return available;
  }

  /**
   * Chooses one backend from the available list using the given strategy.
   *
   * @param strategy     the selection strategy
   * @param available    the eligible backend names, in a stable order
   * @param playerCount  returns the current player count of a backend
   * @param lastServer   the player's last backend, used by {@link SelectionStrategy#LAST_SERVER}
   * @param roundRobin   the counter used by {@link SelectionStrategy#ROUND_ROBIN}
   * @param random       the generator used by {@link SelectionStrategy#RANDOM}
   * @return the chosen backend name
   * @throws IllegalArgumentException if {@code available} is empty
   */
  public static String select(SelectionStrategy strategy,
                              List<String> available,
                              ToIntFunction<String> playerCount,
                              @Nullable String lastServer,
                              AtomicInteger roundRobin,
                              Random random) {
    if (available.isEmpty()) {
      throw new IllegalArgumentException("Cannot select a backend: no available backends.");
    }

    return switch (strategy) {
      case LEAST_PLAYERS -> leastPlayers(available, playerCount);
      case LAST_SERVER -> lastServer != null && available.contains(lastServer)
          ? lastServer
          : leastPlayers(available, playerCount);
      case ROUND_ROBIN -> available.get(Math.floorMod(roundRobin.getAndIncrement(), available.size()));
      case RANDOM -> available.get(random.nextInt(available.size()));
    };
  }

  private static String leastPlayers(List<String> available, ToIntFunction<String> playerCount) {
    String best = available.get(0);
    int bestCount = playerCount.applyAsInt(best);

    for (int index = 1; index < available.size(); index++) {
      String candidate = available.get(index);
      int candidateCount = playerCount.applyAsInt(candidate);
      if (candidateCount < bestCount) {
        best = candidate;
        bestCount = candidateCount;
      }
    }

    return best;
  }
}
