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

package com.velocitypowered.proxy.protocol.packet;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.protocol.packet.brigadier.ArgumentPropertyRegistry;
import com.velocitypowered.proxy.util.collect.IdentityHashStrategy;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a packet that contains the list of available commands, implementing {@link MinecraftPacket}.
 *
 * <p>The {@code AvailableCommandsPacket} is responsible for transmitting the set of commands
 * that a player can execute. It provides the necessary information about available commands
 * within the current session or game state.</p>
 */
public class AvailableCommandsPacket implements MinecraftPacket {

  /**
   * Placeholder command used to mark a node as executable during deserialization.
   *
   * <p>This command does nothing and returns {@code 0}, and is used as a stand-in
   * when reconstructing {@link CommandNode} objects from the packet data.</p>
   */
  private static final Command<CommandSource> PLACEHOLDER_COMMAND = source -> 0;

  /**
   * Placeholder requirement predicate that always evaluates to {@code true}.
   *
   * <p>This predicate is used to mark a node as "restricted" during deserialization
   * by assigning it a dummy requirement.</p>
   */
  private static final Predicate<CommandSource> PLACEHOLDER_REQUIREMENT = source -> true;

  /**
   * Denotes a root command node during deserialization.
   *
   * <p>This value is applied to the lower two bits of the flags byte to identify
   * the node type.</p>
   */
  private static final byte NODE_TYPE_ROOT = 0x00;

  /**
   * Denotes a literal command node during deserialization.
   *
   * <p>This value is applied to the lower two bits of the flags byte to identify
   * the node type.</p>
   */
  private static final byte NODE_TYPE_LITERAL = 0x01;

  /**
   * Denotes an argument command node during deserialization.
   *
   * <p>This value is applied to the lower two bits of the flags byte to identify
   * the node type.</p>
   */
  private static final byte NODE_TYPE_ARGUMENT = 0x02;

  /**
   * Mask used to extract the node type from the flag's byte.
   *
   * <p>This is applied via bitwise AND to isolate {@code NODE_TYPE_*} values.</p>
   */
  private static final byte FLAG_NODE_TYPE = 0x03;

  /**
   * Flag indicating that the node is executable.
   *
   * <p>If set, the node is assigned the {@link #PLACEHOLDER_COMMAND} on deserialization.</p>
   */
  private static final byte FLAG_EXECUTABLE = 0x04;

  /**
   * Flag indicating that the node has a redirect to another node.
   *
   * <p>If set, a redirect index will be read and resolved after the node graph is built.</p>
   */
  private static final byte FLAG_IS_REDIRECT = 0x08;

  /**
   * Flag indicating that the node has a custom suggestion provider.
   *
   * <p>If set, a string representing the suggestion provider's ID will follow
   * the argument data during deserialization.</p>
   */
  private static final byte FLAG_HAS_SUGGESTIONS = 0x10;

  /**
   * Flag indicating that the node is restricted.
   *
   * <p>If set, the {@link #PLACEHOLDER_REQUIREMENT} will be applied to the node
   * to simulate restricted access behavior.</p>
   */
  private static final byte FLAG_IS_RESTRICTED = 0x20;

  /**
   * The root node of the command tree represented by this packet.
   *
   * <p>This field is populated during deserialization and represents the entry point
   * of the reconstructed Brigadier command graph. It is marked {@link MonotonicNonNull}
   * because it is guaranteed to be non-null after {@link #decode(ByteBuf, Direction, ProtocolVersion)}
   * completes successfully, but is initially {@code null}.</p>
   */
  private @MonotonicNonNull RootCommandNode<CommandSource> rootNode;

  /**
   * Returns the root node.
   *
   * @return the root node
   */
  public RootCommandNode<CommandSource> getRootNode() {
    if (rootNode == null) {
      throw new IllegalStateException("Packet not yet deserialized");
    }

    return rootNode;
  }

  /**
   * Decodes the available commands packet from the provided {@link ByteBuf}.
   *
   * <p>This method reads and reconstructs the command node tree from the incoming buffer
   * and initializes the {@code rootNode} reference.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    int commands = ProtocolUtils.readVarInt(buf);
    WireNode[] wireNodes = new WireNode[commands];
    for (int i = 0; i < commands; i++) {
      wireNodes[i] = deserializeNode(buf, i, protocolVersion);
    }

    // Iterate over the deserialized nodes and attempt to form a graph. We also resolve any cycles
    // that exist.
    Queue<WireNode> nodeQueue = new ArrayDeque<>(Arrays.asList(wireNodes));
    while (!nodeQueue.isEmpty()) {
      boolean cycling = false;

      Iterator<WireNode> it = nodeQueue.iterator();
      while (it.hasNext()) {
        WireNode node = it.next();
        if (node.toNode(wireNodes)) {
          cycling = true;
          it.remove();
        }
      }

      if (!cycling) {
        // Uh-oh. We can't cycle. This is bad.
        throw new IllegalStateException("Stopped cycling; the root node can't be built.");
      }
    }

    int rootIdx = ProtocolUtils.readVarInt(buf);
    rootNode = (RootCommandNode<CommandSource>) wireNodes[rootIdx].built;
  }

  /**
   * Encodes the available commands packet into the provided {@link ByteBuf}.
   *
   * <p>This method serializes the root command tree into a flat node array, preserving
   * redirect and child relationships for the target protocol version.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    // Assign all the children an index.
    Deque<CommandNode<CommandSource>> childrenQueue = new ArrayDeque<>(ImmutableList.of(rootNode));
    Object2IntMap<CommandNode<CommandSource>> idMappings = new Object2IntLinkedOpenCustomHashMap<>(
        IdentityHashStrategy.instance());
    while (!childrenQueue.isEmpty()) {
      CommandNode<CommandSource> child = childrenQueue.poll();
      if (!idMappings.containsKey(child)) {
        idMappings.put(child, idMappings.size());
        childrenQueue.addAll(child.getChildren());
        if (child.getRedirect() != null) {
          childrenQueue.add(child.getRedirect());
        }
      }
    }

    // Now serialize the children.
    ProtocolUtils.writeVarInt(buf, idMappings.size());
    for (CommandNode<CommandSource> child : idMappings.keySet()) {
      serializeNode(child, buf, idMappings, protocolVersion);
    }
    ProtocolUtils.writeVarInt(buf, idMappings.getInt(rootNode));
  }

  private static void serializeNode(final CommandNode<CommandSource> node, final ByteBuf buf,
                                    final Object2IntMap<CommandNode<CommandSource>> idMappings, final ProtocolVersion protocolVersion) {
    byte flags = 0;
    if (node.getRedirect() != null) {
      flags |= FLAG_IS_REDIRECT;
    }

    if (node.getCommand() != null) {
      flags |= FLAG_EXECUTABLE;
    }

    if (node.getRequirement() == PLACEHOLDER_REQUIREMENT) {
      flags |= FLAG_IS_RESTRICTED;
    }

    if (node instanceof LiteralCommandNode<?>) {
      flags |= NODE_TYPE_LITERAL;
    } else if (node instanceof ArgumentCommandNode<?, ?>) {
      flags |= NODE_TYPE_ARGUMENT;
      if (((ArgumentCommandNode<CommandSource, ?>) node).getCustomSuggestions() != null) {
        flags |= FLAG_HAS_SUGGESTIONS;
      }
    } else if (!(node instanceof RootCommandNode<?>)) {
      throw new IllegalArgumentException("Unknown node type " + node.getClass().getName());
    }

    buf.writeByte(flags);
    ProtocolUtils.writeVarInt(buf, node.getChildren().size());
    for (CommandNode<CommandSource> child : node.getChildren()) {
      ProtocolUtils.writeVarInt(buf, idMappings.getInt(child));
    }

    if (node.getRedirect() != null) {
      ProtocolUtils.writeVarInt(buf, idMappings.getInt(node.getRedirect()));
    }

    if (node instanceof ArgumentCommandNode<?, ?>) {
      ProtocolUtils.writeString(buf, node.getName());
      ArgumentPropertyRegistry.serialize(buf,
          ((ArgumentCommandNode<CommandSource, ?>) node).getType(), protocolVersion);

      if (((ArgumentCommandNode<CommandSource, ?>) node).getCustomSuggestions() != null) {
        SuggestionProvider<CommandSource> provider = ((ArgumentCommandNode<CommandSource, ?>) node)
            .getCustomSuggestions();

        String name = "minecraft:ask_server";
        if (provider instanceof ProtocolSuggestionProvider) {
          name = ((ProtocolSuggestionProvider) provider).name;
        }
        ProtocolUtils.writeString(buf, name);
      }
    } else if (node instanceof LiteralCommandNode<?>) {
      ProtocolUtils.writeString(buf, node.getName());
    }
  }

  /**
   * Handles this available commands packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates the logic to {@code handler.handle(this)} to populate
   * the command dispatcher with supported commands.</p>
   *
   * @param handler the session handler to process this packet
   * @return {@code true} if handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  private static WireNode deserializeNode(final ByteBuf buf, final int idx, final ProtocolVersion version) {
    byte flags = buf.readByte();
    int[] children = ProtocolUtils.readIntegerArray(buf);
    int redirectTo = -1;
    if ((flags & FLAG_IS_REDIRECT) > 0) {
      redirectTo = ProtocolUtils.readVarInt(buf);
    }

    switch (flags & FLAG_NODE_TYPE) {
      case NODE_TYPE_ROOT:
        return new WireNode(idx, flags, children, redirectTo, null);
      case NODE_TYPE_LITERAL:
        return new WireNode(idx, flags, children, redirectTo, LiteralArgumentBuilder
            .literal(ProtocolUtils.readString(buf)));
      case NODE_TYPE_ARGUMENT:
        String name = ProtocolUtils.readString(buf);
        ArgumentType<?> argumentType = ArgumentPropertyRegistry.deserialize(buf, version);

        RequiredArgumentBuilder<CommandSource, ?> argumentBuilder = RequiredArgumentBuilder
            .argument(name, argumentType);
        if ((flags & FLAG_HAS_SUGGESTIONS) != 0) {
          argumentBuilder.suggests(new ProtocolSuggestionProvider(ProtocolUtils.readString(buf)));
        }
        return new WireNode(idx, flags, children, redirectTo, argumentBuilder);
      default:
        throw new IllegalArgumentException("Unknown node type " + (flags & FLAG_NODE_TYPE));
    }
  }

  private static final class WireNode {

    /**
     * The index of this node in the serialized node array.
     */
    private final int idx;

    /**
     * Bit flags encoding this node's type and properties.
     *
     * <p>Includes flags for node type (literal, argument, root), executable status,
     * redirect presence, suggestion support, and restriction requirement.</p>
     */
    private final byte flags;

    /**
     * Indices of this node’s child nodes in the full node array.
     *
     * <p>Used to resolve the child node references during tree reconstruction.</p>
     */
    private final int[] children;

    /**
     * Index of the node this node redirects to, or {@code -1} if not applicable.
     */
    private final int redirectTo;

    /**
     * The argument builder associated with this node, if it is a literal or argument node.
     *
     * <p>{@code null} for root nodes.</p>
     */
    private final @Nullable ArgumentBuilder<CommandSource, ?> args;

    /**
     * The constructed Brigadier {@link CommandNode} once this node has been resolved.
     */
    private @MonotonicNonNull CommandNode<CommandSource> built;

    /**
     * Whether this node has passed basic validation checks.
     */
    private boolean validated;

    private WireNode(final int idx, final byte flags, final int[] children, final int redirectTo,
                     final @Nullable ArgumentBuilder<CommandSource, ?> args) {
      this.idx = idx;
      this.flags = flags;
      this.children = children;
      this.redirectTo = redirectTo;
      this.args = args;
      this.validated = false;
    }

    void validate(final WireNode[] wireNodes) {
      // Ensure all children exist. Note that we delay checking if the node has already been built;
      // that needs to come after this node is built.
      for (int child : children) {
        if (child < 0 || child >= wireNodes.length) {
          throw new IllegalStateException("Node points to non-existent index " + child);
        }
      }

      if (redirectTo != -1) {
        if (redirectTo < 0 || redirectTo >= wireNodes.length) {
          throw new IllegalStateException("Redirect node points to non-existent index "
              + redirectTo);
        }
      }

      this.validated = true;
    }

    boolean toNode(final WireNode[] wireNodes) {
      if (!this.validated) {
        this.validate(wireNodes);
      }

      if (this.built == null) {
        int type = flags & FLAG_NODE_TYPE;
        if (type == NODE_TYPE_ROOT) {
          this.built = new RootCommandNode<>();
        } else {
          if (args == null) {
            throw new IllegalStateException("Non-root node without args builder!");
          }

          // Add any redirects
          if (redirectTo != -1) {
            WireNode redirect = wireNodes[redirectTo];
            if (redirect.built != null) {
              args.redirect(redirect.built);
            } else {
              // Redirect node does not yet exist
              return false;
            }
          }

          // If executable, add an empty command
          if ((flags & FLAG_EXECUTABLE) != 0) {
            args.executes(PLACEHOLDER_COMMAND);
          }

          // If restricted, add empty requirement
          if ((flags & FLAG_IS_RESTRICTED) != 0) {
            args.requires(PLACEHOLDER_REQUIREMENT);
          }

          this.built = args.build();
        }
      }

      for (int child : children) {
        if (wireNodes[child].built == null) {
          // The child is not yet deserialized. The node can't be built now.
          return false;
        }
      }

      // Associate children with nodes
      for (int child : children) {
        CommandNode<CommandSource> childNode = wireNodes[child].built;
        if (!(childNode instanceof RootCommandNode)) {
          built.addChild(childNode);
        }
      }

      return true;
    }

    @Override
    public String toString() {
      MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this)
          .add("idx", idx)
          .add("flags", flags)
          .add("children", children)
          .add("redirectTo", redirectTo);

      if (args instanceof LiteralArgumentBuilder literal) {
        helper.add("argsLabel", literal.getLiteral());
      } else if (args instanceof RequiredArgumentBuilder required) {
        helper.add("argsName", required.getName());
      }

      return helper.toString();
    }
  }

  /**
   * A placeholder {@link SuggestionProvider} used internally to preserve the suggestion provider
   * name.
   *
   * <p>This value is preserved from the original command graph and used when serializing
   * the node back to the wire format.</p>
   *
   * @param name the suggestion provider identifier to retain
   */
  public record ProtocolSuggestionProvider(String name) implements SuggestionProvider<CommandSource> {

    /**
     * Provides command suggestions for the current context.
     *
     * <p>This implementation returns an empty set of suggestions via {@link SuggestionsBuilder}.</p>
     *
     * @param context the command execution context
     * @param builder the suggestions builder to populate
     * @return a {@link CompletableFuture} containing the final suggestions
     */
    @Override
    public CompletableFuture<Suggestions> getSuggestions(final CommandContext<CommandSource> context,
                                                         final SuggestionsBuilder builder) {
      return builder.buildFuture();
    }
  }

  /**
   * Provides an estimated payload size (in bytes) for encoding this packet.
   *
   * <p>The available-commands graph can be very large and highly variable depending on
   * server configuration and installed plugins. Empirically, even moderately sized
   * setups can exceed tens of kilobytes. To minimize buffer reallocation and copying
   * during encoding, this method returns a conservative fixed estimate of {@code 128 KiB}
   * for the payload size.</p>
   *
   * <p>Note: This estimate is for the packet <em>payload</em> only. The {@code MinecraftEncoder}
   * will add the VarInt-encoded packet ID length on top of this when allocating the final
   * buffer.</p>
   *
   * @param direction the packet direction
   * @param version the Minecraft protocol version
   * @return the estimated payload size in bytes (here, {@code 131072})
   */
  @Override
  public int encodeSizeHint(final Direction direction, final ProtocolVersion version) {
    // This is a very complex packet to encode. Paper 1.21.10 + Velocity with Spark has a size of
    // 30,334, but this is likely on the lower side. We'll use 128KiB as a more realistically-sized
    // amount.
    return 128 * 1024;
  }
}
