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
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a mod-specific argument type with custom binary data attached.
 *
 * <p>This class allows external mods or extensions to define their own command argument
 * types, identified by a namespaced {@link ArgumentIdentifier} and accompanied by
 * serialized {@link ByteBuf} data.</p>
 *
 * <p>Note: This type is not parseable or suggestible through Brigadier and exists primarily
 * to preserve compatibility with extended command metadata during serialization and
 * deserialization.</p>
 */
public class ModArgumentProperty implements ArgumentType<ByteBuf> {

  /**
   * The identifier representing the mod-defined argument type.
   */
  private final ArgumentIdentifier identifier;

  /**
   * The raw serialized argument data associated with the mod argument.
   */
  private final ByteBuf data;

  /**
   * Constructs a new {@code ModArgumentProperty} with the specified identifier and binary data.
   *
   * @param identifier the argument identifier (e.g., {@code "modid:custom_type"})
   * @param data the serialized argument data buffer (copied as read-only)
   */
  public ModArgumentProperty(final ArgumentIdentifier identifier, final ByteBuf data) {
    this.identifier = identifier;
    this.data = Unpooled.unreleasableBuffer(data.asReadOnly());
  }

  /**
   * Returns the identifier of this mod argument.
   *
   * @return the mod argument identifier
   */
  public ArgumentIdentifier getIdentifier() {
    return identifier;
  }

  /**
   * Returns a sliced, read-only copy of the internal argument data buffer.
   *
   * @return the mod argument's serialized data
   */
  public ByteBuf getData() {
    return data.slice();
  }

  @Override
  public final ByteBuf parse(final StringReader reader) throws CommandSyntaxException {
    throw new UnsupportedOperationException();
  }

  @Override
  public final <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context,
                                                                  final SuggestionsBuilder builder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final Collection<String> getExamples() {
    throw new UnsupportedOperationException();
  }
}
