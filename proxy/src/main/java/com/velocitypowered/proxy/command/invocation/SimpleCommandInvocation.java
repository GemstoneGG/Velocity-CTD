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

package com.velocitypowered.proxy.command.invocation;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.command.brigadier.StringArrayArgumentType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Implements {@link SimpleCommand.Invocation}.
 */
public final class SimpleCommandInvocation extends AbstractCommandInvocation<String[]> implements SimpleCommand.Invocation {

  /**
   * A factory for creating {@link SimpleCommandInvocation} instances.
   */
  public static final Factory FACTORY = new Factory();

  /**
   * Factory class for creating instances of {@link SimpleCommand.Invocation}.
   *
   * <p>This class implements the {@link CommandInvocationFactory} interface and provides
   * a method to create new {@link SimpleCommand.Invocation} instances. It is responsible
   * for reading the command alias and arguments from the parsed command nodes and arguments.</p>
   */
  public static class Factory implements CommandInvocationFactory<SimpleCommand.Invocation> {

    /**
     * Creates a {@link SimpleCommand.Invocation} from the parsed command context.
     *
     * <p>This method extracts the command alias and arguments from the provided parse results
     * and returns a new {@link SimpleCommandInvocation} instance representing the invocation.</p>
     *
     * @param source the command source (e.g., player or console)
     * @param nodes the parsed command nodes from Brigadier
     * @param arguments the parsed arguments mapped by name
     * @return a new {@link SimpleCommandInvocation} instance
     */
    @Override
    public SimpleCommand.Invocation create(final CommandSource source, final List<? extends ParsedCommandNode<?>> nodes,
                                           final Map<String, ? extends ParsedArgument<?, ?>> arguments) {
      final String alias = VelocityCommands.readAlias(nodes);
      final String[] args = VelocityCommands.readArguments(arguments, String[].class, StringArrayArgumentType.EMPTY);
      return new SimpleCommandInvocation(source, alias, args);
    }
  }

  /**
   * The alias used to invoke the command.
   */
  private final String alias;

  SimpleCommandInvocation(final CommandSource source, final String alias, final String[] arguments) {
    super(source, arguments);
    this.alias = Preconditions.checkNotNull(alias, "alias");
  }

  @Override
  public String alias() {
    return this.alias;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    if (!super.equals(o)) {
      return false;
    }

    final SimpleCommandInvocation that = (SimpleCommandInvocation) o;
    return this.alias.equals(that.alias);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + this.alias.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "SimpleCommandInvocation{"
        + "source='" + this.source() + '\''
        + ", alias='" + this.alias + '\''
        + ", arguments='" + Arrays.toString(this.arguments()) + '\''
        + '}';
  }
}
