/*
 * Copyright (C) 2018-2021 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An event handler that returns an {@link EventTask} to await on.
 *
 * @param <E> event type
 */
@FunctionalInterface
public interface AwaitingEventExecutor<E> extends EventHandler<E> {

  /**
   * This method is not supported for {@link AwaitingEventExecutor} and will throw an exception
   * if invoked. Use {@link #executeAsync(Object)} instead.
   *
   * @param event the event to handle
   * @throws UnsupportedOperationException always
   */
  @Override
  default void execute(E event) {
    throw new UnsupportedOperationException(
        "This event handler can only be invoked asynchronously.");
  }

  @Nullable EventTask executeAsync(E event);
}
