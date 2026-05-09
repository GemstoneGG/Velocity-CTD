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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A depot represents a map-like structure that stores a certain type of {@link V values}, indexed by a key of type {@link K}
 * within a Redis database/hash. It is used to store and retrieve objects in and from the database/hash.
 *
 * <p>Within VelocityRedis, the depot is used to store and retrieve objects like online players and server info from other proxies.</p>
 *
 * @param <K> the key type used to index values in the depot
 * @param <V> the value type stored in the depot, extending {@link DepotEntry}
 */
public interface Depot<K, V extends DepotEntry<K, V>> {

  /**
   * Checks whether the depot contains a value for the given key.
   *
   * @param key the key to check for
   * @return {@code true} if the depot contains a value for the given key, {@code false} otherwise
   */
  boolean contains(@NotNull K key);

  /**
   * Retrieves the value for the given key if it exists.
   *
   * @param key the key to retrieve the value for
   * @return the value for the given key, or {@code null} if it does not exist
   */
  @Nullable
  V get(K key);

  /**
   * Update or insert the given value into the depot.
   *
   * @param value the value to upsert
   */
  void upsert(@NotNull V value);

  /**
   * Removes the value for the given key if it exists.
   *
   * @param key the key to remove the value for
   * @return the removed value, or {@code null} if it did not exist
   */
  @Nullable
  V remove(@NotNull K key);

  /**
   * Returns a collection of all values in the depot.
   *
   * @return a collection of all values in the depot
   */
  Collection<V> values();

  /**
   * Returns a collection of all keys in the depot.
   *
   * @return a collection of all keys in the depot
   */
  Collection<String> keys();

  /**
   * Returns the number of entries in the depot.
   *
   * @return the number of entries in the depot
   */
  default int size() {
    return keys().size();
  }
}
