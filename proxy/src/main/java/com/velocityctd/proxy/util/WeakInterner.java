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

package com.velocityctd.proxy.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An interner with weakly held values, looked up by a separate key. The key can be built from
 * decoded data before the value is created, and works for types without a value-based
 * {@code equals}. Keys of collected values are removed on the next call.
 *
 * <p>Thread-safe. Under contention the factory may run more than once for a key, but only one
 * value is ever returned.</p>
 *
 * @param <K> the key, with value-based {@code equals} and {@code hashCode}, not referencing the
 *            value
 * @param <V> the value, which must be immutable
 */
public final class WeakInterner<K, V> {

  private final ConcurrentMap<K, ValueReference<K, V>> values = new ConcurrentHashMap<>();
  private final ReferenceQueue<V> collected = new ReferenceQueue<>();

  /**
   * Returns the shared value for {@code key}, creating it with {@code factory} when there is none.
   *
   * @param key the value-based lookup key
   * @param factory creates the value when no live one exists for the key
   * @return the shared value
   */
  public V intern(K key, Function<? super K, ? extends V> factory) {
    expungeCollected();

    while (true) {
      final ValueReference<K, V> current = this.values.get(key);
      if (current != null) {
        final V shared = current.get();
        if (shared != null) {
          return shared;
        }
      }

      final V created = factory.apply(key);
      final ValueReference<K, V> fresh = new ValueReference<>(key, created, this.collected);
      final boolean stored = current == null
          ? this.values.putIfAbsent(key, fresh) == null
          : this.values.replace(key, current, fresh);
      if (stored) {
        return created;
      }
    }
  }

  /**
   * Returns the shared value for {@code key}, storing {@code value} when there is none.
   *
   * @param key the value-based lookup key describing {@code value}
   * @param value the candidate value
   * @return the shared value, which is {@code value} itself when it is the first of its kind
   */
  public V intern(K key, V value) {
    return intern(key, ignored -> value);
  }

  /**
   * Returns the number of keys held, after removing those of collected values.
   *
   * @return the number of held keys
   */
  public int size() {
    expungeCollected();
    return this.values.size();
  }

  @SuppressWarnings("unchecked")
  private void expungeCollected() {
    ValueReference<K, V> reference;
    while ((reference = (ValueReference<K, V>) this.collected.poll()) != null) {
      this.values.remove(reference.key, reference);
    }
  }

  private static final class ValueReference<K, V> extends WeakReference<V> {

    private final K key;

    private ValueReference(K key, V value, ReferenceQueue<V> queue) {
      super(value, queue);
      this.key = key;
    }
  }
}
