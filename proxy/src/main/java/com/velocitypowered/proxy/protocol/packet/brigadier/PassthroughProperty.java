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

package com.velocitypowered.proxy.protocol.packet.brigadier;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A generic {@link ArgumentType} wrapper that allows preserving deserialized argument
 * values that are not natively handled by Brigadier or Velocity.
 *
 * <p>{@code PassthroughProperty} acts as a transparent container for argument types that
 * were deserialized using a known {@link ArgumentPropertySerializer} but do not map to
 * a recognized Brigadier type. This ensures the structure is retained for serialization,
 * even if it's not parseable at runtime.</p>
 *
 * <p>Used internally by {@link ArgumentPropertyRegistry} to maintain argument metadata
 * when forwarding or re-encoding commands.</p>
 *
 * @param <T> the type of the deserialized object being passed through
 */
class PassthroughProperty<T> implements ArgumentType<T> {

  /**
   * The identifier for the argument type being preserved.
   */
  private final ArgumentIdentifier identifier;

  /**
   * The serializer used to originally deserialize the result.
   */
  private final ArgumentPropertySerializer<T> serializer;

  /**
   * The preserved deserialized result (nullable).
   */
  private final @Nullable T result;

  PassthroughProperty(final ArgumentIdentifier identifier, final ArgumentPropertySerializer<T> serializer,
                      @Nullable final T result) {
    this.identifier = identifier;
    this.serializer = serializer;
    this.result = result;
  }

  public ArgumentIdentifier getIdentifier() {
    return identifier;
  }

  public ArgumentPropertySerializer<T> getSerializer() {
    return serializer;
  }

  public @Nullable T getResult() {
    return result;
  }

  @Override
  public T parse(final StringReader reader) {
    throw new UnsupportedOperationException();
  }
}
