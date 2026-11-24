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
import com.velocityctd.api.queue.ServerGroup;
import com.velocitypowered.proxy.config.VelocityConfiguration.Queue.ServerGroupDefinition;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * Runtime representation of a configured server group.
 *
 * <p>Each group owns the state needed by stateful selection strategies, namely the
 * round-robin counter and the random number generator.</p>
 */
public final class VelocityServerGroup implements ServerGroup {

  private final String name;
  private final List<String> servers;
  private final SelectionStrategy strategy;
  private final int maxPlayers;
  private final AtomicInteger roundRobin = new AtomicInteger();
  private final Random random = new Random();

  /**
   * Creates a runtime group from its configuration definition.
   *
   * @param definition the parsed configuration definition
   */
  public VelocityServerGroup(@NotNull ServerGroupDefinition definition) {
    this(definition.name(), definition.servers(), definition.strategy(), definition.maxPlayers());
  }

  /**
   * Creates a runtime group from explicit values.
   *
   * @param name       the group name
   * @param servers    the backend names in selection order
   * @param strategy   the selection strategy
   * @param maxPlayers the per-backend player limit, or {@code -1} for no limit
   */
  public VelocityServerGroup(@NotNull String name, @NotNull List<String> servers,
                             @NotNull SelectionStrategy strategy, int maxPlayers) {
    this.name = name;
    this.servers = List.copyOf(servers);
    this.strategy = strategy;
    this.maxPlayers = maxPlayers;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<String> getServers() {
    return servers;
  }

  @Override
  public SelectionStrategy getStrategy() {
    return strategy;
  }

  @Override
  public int getMaxPlayers() {
    return maxPlayers;
  }

  /**
   * Returns the counter used by the round-robin strategy.
   *
   * @return the round-robin counter
   */
  public AtomicInteger getRoundRobin() {
    return roundRobin;
  }

  /**
   * Returns the generator used by the random strategy.
   *
   * @return the random number generator
   */
  public Random getRandom() {
    return random;
  }
}
