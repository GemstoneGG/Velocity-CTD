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

package com.velocitypowered.proxy.event;

import com.google.common.reflect.TypeToken;
import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.EventTask;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.lanternpowered.lmbda.LambdaFactory;
import org.lanternpowered.lmbda.LambdaType;

final class CustomHandlerAdapter<F> {

  /**
   * The name of this handler adapter, typically identifying its purpose or usage style.
   */
  final String name;

  /**
   * A function that converts an instance of the functional interface {@code F}
   * into a handler function that consumes a target and an event and returns an {@link EventTask}.
   */
  private final Function<F, BiFunction<Object, Object, EventTask>> handlerBuilder;

  /**
   * A predicate used to determine if a method should be handled by this adapter.
   */
  final Predicate<Method> filter;

  /**
   * A validator function that performs custom checks on candidate methods.
   * It may add error messages to the provided list if validation fails.
   */
  final BiConsumer<Method, List<String>> validator;

  /**
   * The lambda type associated with the functional interface {@code F}.
   */
  private final LambdaType<F> functionType;

  /**
   * The {@link MethodHandles.Lookup} used to access the target method.
   */
  private final MethodHandles.Lookup methodHandlesLookup;

  @SuppressWarnings("unchecked")
  CustomHandlerAdapter(final String name, final Predicate<Method> filter,
                       final BiConsumer<Method, List<String>> validator,
                       final TypeToken<F> invokeFunctionType,
                       final Function<F, BiFunction<Object, Object, EventTask>> handlerBuilder,
                       final MethodHandles.Lookup methodHandlesLookup) {
    this.name = name;
    this.filter = filter;
    this.validator = validator;
    this.functionType = (LambdaType<F>) LambdaType.of(invokeFunctionType.getRawType());
    this.handlerBuilder = handlerBuilder;
    this.methodHandlesLookup = methodHandlesLookup;
  }

  UntargetedEventHandler buildUntargetedHandler(final Method method) throws IllegalAccessException {
    final MethodHandle methodHandle = methodHandlesLookup.unreflect(method);
    final MethodHandles.Lookup defineLookup = MethodHandles.privateLookupIn(
        method.getDeclaringClass(), methodHandlesLookup);
    final LambdaType<F> lambdaType = functionType.defineClassesWith(defineLookup);
    final F invokeFunction = LambdaFactory.create(lambdaType, methodHandle);
    final BiFunction<Object, Object, EventTask> handlerFunction =
        handlerBuilder.apply(invokeFunction);
    return targetInstance -> new EventHandler<>() {

      @Override
      public void execute(final Object event) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @Nullable EventTask executeAsync(final Object event) {
        return handlerFunction.apply(targetInstance, event);
      }
    };
  }
}
