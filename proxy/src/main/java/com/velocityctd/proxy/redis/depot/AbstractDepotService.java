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

import com.velocityctd.proxy.redis.provider.RedisProvider;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Represents an abstract implementation of the {@link DepotService} interface.
 *
 * <p>This class provides the foundational behavior for interacting with
 * a Redis-backed {@link Depot}. Subclasses extend this implementation to
 * specialize the handling of particular {@link DepotEntry} types.</p>
 *
 * @param <K> the key type used by the depot
 * @param <V> the value type stored in the depot, extending {@link DepotEntry}
 */
public abstract non-sealed class AbstractDepotService<K, V extends DepotEntry<K, V>>
        implements DepotService<K, V> {

  /**
   * The underlying Redis-backed depot used to store and retrieve entries.
   */
  protected final Depot<K, V> depot;

  /**
   * Constructs a new {@link AbstractDepotService}.
   *
   * @param valueClass the class type of the value in the depot
   * @param provider the redis provider implementation instance
   */
  public AbstractDepotService(Class<V> valueClass, @NotNull RedisProvider provider) {
    this.depot = provider.createDepot(valueClass);
  }

  /**
   * Retrieves a value from the depot by its key.
   *
   * @param key the key to retrieve
   * @return the matching depot entry, or {@code null} if not found
   */
  @Override
  public @Nullable V get(K key) {
    return this.depot.get(key);
  }

  /**
   * Retrieves an unmodifiable list of all entries stored in the depot.
   *
   * @return an unmodifiable list of all depot entries
   */
  @Override
  @Unmodifiable
  public @NotNull List<V> getAll() {
    return List.copyOf(this.depot.values());
  }

  /**
   * Queries all entries in the depot that match the given predicate.
   *
   * @param predicate the predicate used to filter entries
   * @return a collection of matching entries
   */
  @Override
  public @NotNull Collection<V> queryAll(Predicate<V> predicate) {
    return this.depot.values().stream().filter(predicate).toList();
  }

  @Override
  public void teardown() {
    // Nothing by default
  }
}
