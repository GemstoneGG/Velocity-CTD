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

package com.velocityctd.proxy.redis.depot;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents an entry in a {@link Depot}.
 *
 * <p>This class serves as the base type for objects stored in a Redis-backed
 * {@link Depot}. Each entry is uniquely identified by a key and can be
 * inserted, updated, or removed via its associated depot instance.</p>
 *
 * @param <K> the type of the unique identifier for the depot entry
 * @param <T> the concrete {@link DepotEntry} subclass type
 */
public abstract class DepotEntry<K, T extends DepotEntry<K, T>> {

  /**
   * The unique identifier assigned to this entry within the depot.
   */
  private final K uniqueId;

  /**
   * The depot to which this entry is currently associated.
   *
   * <p>Marked {@code transient} because depot associations are not serialized
   * and are re-established by the depot implementation when entries are loaded.</p>
   */
  private transient @MonotonicNonNull Depot<K, T> depot;

  /**
   * Constructs a new {@link DepotEntry}.
   *
   * @param key the unique id of the entry
   */
  public DepotEntry(K key) {
    this.uniqueId = key;
  }

  /**
   * Updates or inserts the current instance into its associated {@link Depot}.
   */
  @SuppressWarnings("unchecked")
  public void upsert() {
    if (this.depot != null) {
      this.depot.upsert((T) this);
    }
  }

  /**
   * Removes this entry from its associated {@link Depot}, if it is currently present.
   * This operation is performed using the entry's unique identifier.
   */
  public void remove() {
    if (this.depot != null) {
      this.depot.remove(this.uniqueId);
    }
  }

  /**
   * Gets the unique identifier of this entry.
   *
   * @return the unique identifier of this entry
   */
  public K getUniqueId() {
    return uniqueId;
  }

  /**
   * Sets the associated {@link Depot}.
   *
   * @param depot the depot to set
   */
  @ApiStatus.Internal
  public void setDepot(Depot<K, T> depot) {
    if (depot != null) {
      this.depot = depot;
    }
  }

  /**
   * Determines whether this entry is equal to another object.
   *
   * <p>Two {@link DepotEntry} instances are considered equal if they are of the same
   * concrete class and have the same {@link #uniqueId} value.</p>
   *
   * @param o the object to compare against
   * @return {@code true} if the objects are equal, otherwise {@code false}
   */
  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DepotEntry<?, ?> that = (DepotEntry<?, ?>) o;
    return Objects.equals(uniqueId, that.uniqueId);
  }

  /**
   * Computes the hash code for this entry using its unique identifier.
   *
   * @return the hash code based on {@link #uniqueId}
   */
  @Override
  public int hashCode() {
    return Objects.hashCode(uniqueId);
  }
}
