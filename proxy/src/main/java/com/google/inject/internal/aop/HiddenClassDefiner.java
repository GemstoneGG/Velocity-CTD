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

package com.google.inject.internal.aop;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * {@link ClassDefiner} that defines classes using {@code MethodHandles.Lookup#defineHiddenClass}.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class HiddenClassDefiner implements ClassDefiner {

  private static final Object HIDDEN_CLASS_OPTIONS;
  private static final Method HIDDEN_DEFINE_METHOD;

  static {
    try {
      HIDDEN_CLASS_OPTIONS = classOptions("NESTMATE");
      HIDDEN_DEFINE_METHOD =
          Lookup.class.getMethod(
              "defineHiddenClass", byte[].class, boolean.class, HIDDEN_CLASS_OPTIONS.getClass());
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Override
  public Class<?> define(Class<?> hostClass, byte[] bytecode) throws Exception {
    Lookup lookup = MethodHandles.privateLookupIn(hostClass, MethodHandles.lookup());
    Lookup definedLookup =
        (Lookup)
            HIDDEN_DEFINE_METHOD.invoke(lookup, bytecode, false, HIDDEN_CLASS_OPTIONS);
    return definedLookup.lookupClass();
  }

  /** Creates {@link MethodHandles.Lookup.ClassOption} array with the named options. */
  @SuppressWarnings("unchecked")
  private static Object classOptions(String... options) throws ClassNotFoundException {
    @SuppressWarnings("rawtypes") // Unavoidable, only way to use Enum.valueOf
    Class optionClass = Class.forName(Lookup.class.getName() + "$ClassOption");
    Object classOptions = Array.newInstance(optionClass, options.length);
    for (int i = 0; i < options.length; i++) {
      Array.set(classOptions, i, Enum.valueOf(optionClass, options[i]));
    }

    return classOptions;
  }
}
