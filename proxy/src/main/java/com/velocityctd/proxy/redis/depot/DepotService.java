/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.redis.depot;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Represents a service for interacting with a Redis-backed {@link Depot}.
 *
 * <p>Implementations of this interface provide typed access to a depot's stored
 * entries, allowing retrieval, querying, and teardown operations.</p>
 *
 * @param <K> the key type used to index entries in the depot
 * @param <V> the value type stored in the depot, extending {@link DepotEntry}
 */
public sealed interface DepotService<K, V extends DepotEntry<K, V>> permits AbstractDepotService {

  /**
   * Retrieves the value for the given key if it exists.
   *
   * @param key the key to retrieve the value for
   * @return the value for the given key, or {@code null} if it does not exist
   */
  @Nullable V get(K key);

  /**
   * Returns a collection of all values in the depot.
   *
   * @return an immutable collection of all values in the depot
   */
  @NotNull
  @Unmodifiable
  List<V> getAll();

  /**
   * Returns a collection of all values in the depot that match the given predicate.
   *
   * @param predicate the predicate to filter the values by
   * @return a collection of all values in the depot that match the given predicate
   */
  @NotNull Collection<V> queryAll(Predicate<V> predicate);

  /**
   * Tears down the depot service.
   */
  void teardown();
}
