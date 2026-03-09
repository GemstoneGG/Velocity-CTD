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

package com.velocitypowered.natives.util;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A loader for native code.
 *
 * @param <T> the interface of the instance to load
 */
public final class NativeCodeLoader<T> implements Supplier<T> {

  private final Variant<T> selected;

  NativeCodeLoader(final List<Variant<T>> variants) {
    this.selected = getVariant(variants);
  }

  @Override
  public T get() {
    return selected.constructed;
  }

  private static <T> Variant<T> getVariant(final List<Variant<T>> variants) {
    for (Variant<T> variant : variants) {
      T got = variant.get();
      if (got == null) {
        continue;
      }

      return variant;
    }
    throw new IllegalArgumentException("Can't find any suitable variants");
  }

  /**
   * Returns the name of the successfully loaded variant.
   *
   * @return the variant name
   */
  public String getLoadedVariant() {
    return selected.name;
  }

  static class Variant<T> {

    /**
     * Current status of this variant (available, setup complete, failed, etc.).
     */
    private Status status;

    /**
     * The code to run during setup, usually used to initialize native libraries.
     */
    private final Runnable setup;

    /**
     * The name of this variant (e.g., "libdeflate", "java").
     */
    private final String name;

    /**
     * A factory to create the variant implementation.
     */
    private final Supplier<T> object;

    /**
     * The instantiated result, after successful setup.
     */
    private T constructed;

    Variant(final BooleanSupplier possiblyAvailable, final Runnable setup, final String name, final T object) {
      this(possiblyAvailable, setup, name, () -> object);
    }

    Variant(final BooleanSupplier possiblyAvailable, final Runnable setup, final String name, final Supplier<T> object) {
      this.status = possiblyAvailable.getAsBoolean() ? Status.POSSIBLY_AVAILABLE : Status.NOT_AVAILABLE;
      this.setup = setup;
      this.name = name;
      this.object = object;
    }

    public @Nullable T get() {
      if (status == Status.NOT_AVAILABLE || status == Status.SETUP_FAILURE) {
        return null;
      }

      // Make sure setup happens only once
      if (status == Status.POSSIBLY_AVAILABLE) {
        try {
          setup.run();
          constructed = object.get();
          status = Status.SETUP;
        } catch (Exception e) {
          status = Status.SETUP_FAILURE;
          return null;
        }
      }

      return constructed;
    }
  }

  private enum Status {

    /**
     * Variant is not available on the current platform.
     */
    NOT_AVAILABLE,

    /**
     * Variant may be available and needs setup.
     */
    POSSIBLY_AVAILABLE,

    /**
     * Setup completed and variant is ready for use.
     */
    SETUP,

    /**
     * Setup failed and the variant should be skipped.
     */
    SETUP_FAILURE
  }

  /**
   * A {@link BooleanSupplier} that always returns {@code true}, indicating availability.
   */
  static final BooleanSupplier ALWAYS = () -> true;
}
