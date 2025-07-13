/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

package com.velocitypowered.proxy.util.collect;

import it.unimi.dsi.fastutil.Hash.Strategy;

/**
 * An identity hash strategy for fastutil.
 *
 * @param <T> the type
 */
public final class IdentityHashStrategy<T> implements Strategy<T> {

  /**
   * A reusable singleton instance of {@code IdentityHashStrategy}.
   */
  @SuppressWarnings("rawtypes")
  private static final IdentityHashStrategy INSTANCE = new IdentityHashStrategy();

  /**
   * Returns a singleton instance of {@link IdentityHashStrategy}.
   *
   * @param <T> the type for which the strategy applies
   * @return a shared identity hash strategy instance
   */
  public static <T> Strategy<T> instance() {
    //noinspection unchecked
    return INSTANCE;
  }

  @Override
  public int hashCode(final T o) {
    return System.identityHashCode(o);
  }

  @Override
  public boolean equals(final T a, final T b) {
    return a == b;
  }
}
