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

package com.velocitypowered.proxy.event;

import com.velocitypowered.api.event.AwaitingEventExecutor;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.EventTask;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Core class for invoking event handlers registered by plugins.
 */
public interface UntargetedEventHandler {

  /**
   * Binds this untargeted event handler to a specific instance of the target class and
   * returns an {@link EventHandler} that can be executed with events.
   *
   * @param targetInstance the target plugin instance or listener class
   * @return a concrete {@link EventHandler} for the given target
   */
  EventHandler<Object> buildHandler(Object targetInstance);

  /**
   * Interface used for invoking listeners that return {@link EventTask}.
   */
  interface EventTaskHandler extends UntargetedEventHandler {

    /**
     * Executes the handler logic on the given target instance and event, returning an {@link EventTask}.
     *
     * @param targetInstance the listener instance
     * @param event the event object being dispatched
     * @return an {@link EventTask} or {@code null} if no task is required
     */
    @Nullable EventTask execute(Object targetInstance, Object event);

    @Override
    default EventHandler<Object> buildHandler(final Object targetInstance) {
      return (AwaitingEventExecutor<Object>) event -> execute(targetInstance, event);
    }
  }

  /**
   * Interface used for invoking listeners that return nothing.
   */
  interface VoidHandler extends UntargetedEventHandler {

    /**
     * Executes the handler logic on the given target instance and event.
     *
     * @param targetInstance the listener instance
     * @param event the event object being dispatched
     */
    void execute(Object targetInstance, Object event);

    @Override
    default EventHandler<Object> buildHandler(final Object targetInstance) {
      return (AwaitingEventExecutor<Object>) event -> {
        execute(targetInstance, event);
        return null;
      };
    }
  }

  /**
   * Interface used for invoking listeners that take a {@link Continuation} along with an event.
   */
  interface WithContinuationHandler extends UntargetedEventHandler {

    /**
     * Executes the handler logic with access to a continuation that must be resumed.
     *
     * @param targetInstance the listener instance
     * @param event the event object being dispatched
     * @param continuation the continuation callback to resume event execution
     */
    void execute(Object targetInstance, Object event, Continuation continuation);

    @Override
    default EventHandler<Object> buildHandler(final Object targetInstance) {
      return (AwaitingEventExecutor<Object>) event -> EventTask.withContinuation(continuation ->
          execute(targetInstance, event, continuation));
    }
  }
}
