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

package com.velocitypowered.proxy.command;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.proxy.command.brigadier.VelocityArgumentCommandNode;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Copies the nodes of a {@link RootCommandNode} to a possibly non-empty destination
 * {@link RootCommandNode}, respecting the requirements satisfied by a given command source.
 *
 * @param <S> the type of the source to inject the nodes for
 */
public final class CommandGraphInjector<S> {

  private static final StringRange ALIAS_RANGE = StringRange.at(0);

  private static final StringReader ALIAS_READER = new StringReader("");

  private final @GuardedBy("lock") CommandDispatcher<S> dispatcher;

  private final Lock lock;

  CommandGraphInjector(CommandDispatcher<S> dispatcher, Lock lock) {
    this.dispatcher = Preconditions.checkNotNull(dispatcher, "dispatcher");
    this.lock = Preconditions.checkNotNull(lock, "lock");
  }

  // The term "source" is ambiguous here. We use "origin" when referring to
  // the root node, we are copying nodes from the destination node.

  /**
   * Adds the node from the root node of this injector to the given root node, respecting the
   * requirements satisfied by the given source.
   *
   * <p>Before adding a literal with the same name as one previously contained
   * in the destination node, the old node is removed from the destination node.
   *
   * @param dest   the root node to add the permissible nodes to
   * @param source the command source to inject the nodes for
   */
  public void inject(RootCommandNode<S> dest, S source) {
    lock.lock();
    try {
      Map<CommandNode<S>, CommandNode<S>> done = new IdentityHashMap<>();
      RootCommandNode<S> origin = this.dispatcher.getRoot();
      CommandContextBuilder<S> rootContext = new CommandContextBuilder<>(this.dispatcher, source, origin, 0);

      // Filter alias nodes
      for (CommandNode<S> node : origin.getChildren()) {
        if (!node.canUse(source)) {
          continue;
        }

        CommandContextBuilder<S> context = rootContext.copy()
            .withNode(node, ALIAS_RANGE);
        if (!node.canUse(context, ALIAS_READER)) {
          continue;
        }

        LiteralCommandNode<S> asLiteral = (LiteralCommandNode<S>) node;
        LiteralCommandNode<S> copy = asLiteral.createBuilder().build();
        VelocityArgumentCommandNode<S, ?> argsNode = VelocityCommands.getArgumentsNode(asLiteral);
        if (argsNode == null) {
          // This literal is associated with a BrigadierCommand, filter normally.
          this.copyChildren(node, copy, source, done);
        } else {
          // Copy all children nodes (arguments node and hints)
          for (CommandNode<S> child : node.getChildren()) {
            copy.addChild(child);
          }
        }

        this.addAlias(copy, dest);
      }
    } finally {
      lock.unlock();
    }
  }

  private @Nullable CommandNode<S> filterNode(CommandNode<S> node, S source, Map<CommandNode<S>, CommandNode<S>> done) {
    if (done.containsKey(node)) {
      return done.get(node);
    }

    // We only check the non-context requirement when filtering alias nodes.
    // Otherwise, we would need to manually craft context builder and reader instances,
    // which are both incorrect and inefficient.
    // The reason why we can do so for alias
    // literals is due to the empty string being a valid and expected input by
    // the context-aware requirement (when suggesting the literal name).
    if (!node.canUse(source)) {
      return null;
    }

    ArgumentBuilder<S, ?> builder = node.createBuilder();
    if (node.getRedirect() != null) {
      // Redirects to non-Brigadier commands are not supported. Luckily,
      // we don't expose the root node to API users, so they can't access
      // nodes associated with other commands.
      CommandNode<S> target = this.filterNode(node.getRedirect(), source, done);
      builder.forward(target, builder.getRedirectModifier(), builder.isFork());
    }

    CommandNode<S> result = builder.build();
    done.put(node, result);
    this.copyChildren(node, result, source, done);
    return result;
  }

  private void copyChildren(CommandNode<S> parent, CommandNode<S> dest, S source, Map<CommandNode<S>, CommandNode<S>> done) {
    for (CommandNode<S> child : parent.getChildren()) {
      CommandNode<S> filtered = this.filterNode(child, source, done);
      if (filtered != null) {
        dest.addChild(filtered);
      }
    }
  }

  private void addAlias(LiteralCommandNode<S> node, RootCommandNode<S> dest) {
    dest.removeChildByName(node.getName());
    dest.addChild(node);
  }
}
