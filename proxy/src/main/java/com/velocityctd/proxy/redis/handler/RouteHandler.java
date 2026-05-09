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

package com.velocityctd.proxy.redis.handler;

import java.util.function.Consumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a route registration for a specific data type received from Redis.
 *
 * <p>A {@link RouteHandler} defines how a particular data type should be handled
 * once received as a one-way message from Redis.</p>
 *
 * @param <T> the type of data handled by this route registration
 */
public final class RouteHandler<T> {

  /**
   * The class type of the data associated with this route registration.
   */
  private final Class<T> dataClass;

  /**
   * The {@link Consumer} that processes data of type {@code T}.
   */
  private final Consumer<T> consumer;

  /**
   * Constructs a new {@link RouteHandler}.
   *
   * @param dataClass the data class this registration handles
   * @param consumer the consumer that will process incoming data of type {@code T}
   */
  public RouteHandler(Class<T> dataClass, Consumer<T> consumer) {
    this.dataClass = dataClass;
    this.consumer = consumer;
  }

  /**
   * Creates a new {@link RouteHandler} for the given data class and consumer handler.
   *
   * @param dataClass the concrete data class this route handles
   * @param consumer the consumer to invoke when data of type {@code T} is received
   * @param <T> the type of the data handled by the consumer
   * @return a new {@link RouteHandler} instance
   */
  @Contract("_, _ -> new")
  public static <T> @NotNull RouteHandler<T> consumer(Class<T> dataClass,
                                                      Consumer<T> consumer) {
    return new RouteHandler<>(dataClass, consumer);
  }

  /**
   * Gets the data class associated with this route registration.
   *
   * @return the {@link Class} object representing the data type handled by this registration
   */
  public Class<T> getDataClass() {
    return dataClass;
  }

  /**
   * Gets the {@link Consumer} responsible for handling data associated with this route.
   *
   * @return the consumer tied to this route registration
   */
  public Consumer<T> getConsumer() {
    return consumer;
  }
}
