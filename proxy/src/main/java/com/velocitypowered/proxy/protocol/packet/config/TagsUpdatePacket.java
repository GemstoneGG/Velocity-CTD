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

package com.velocitypowered.proxy.protocol.packet.config;

import com.google.common.collect.ImmutableMap;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * The {@code TagsUpdatePacket} class represents a packet sent to update the tags
 * used by the Minecraft client. Tags are used in various parts of the game to group
 * blocks, items, entities, and other objects under common categories.
 *
 * <p>This packet is typically sent to clients when they join a server or when
 * the server needs to update the list of tags for the client, ensuring that
 * the client has the most up-to-date tag information.</p>
 */
public class TagsUpdatePacket implements MinecraftPacket {

  /**
   * A map of registry keys to tag definitions.
   *
   * <p>The outer key is the registry name (e.g., {@code minecraft:block}), and the inner map
   * contains tag names mapped to an array of integer entry IDs.</p>
   */
  private Map<String, Map<String, int[]>> tags;

  /**
   * Constructs a {@code TagsUpdatePacket} with an explicit tag mapping.
   *
   * @param tags the registry-to-tag structure
   */
  public TagsUpdatePacket(final Map<String, Map<String, int[]>> tags) {
    this.tags = tags;
  }

  /**
   * Constructs an empty {@code TagsUpdatePacket} with no tag data.
   */
  public TagsUpdatePacket() {
    this.tags = Map.of();
  }

  /**
   * Decodes this tags update packet from the provided {@link ByteBuf}.
   *
   * <p>This reads a nested map structure from the buffer, where each outer entry corresponds
   * to a registry (e.g., {@code minecraft:block}), and each inner entry maps tag names
   * to arrays of integer identifiers.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    ImmutableMap.Builder<@NotNull String, @NotNull Map<String, int[]>> builder = ImmutableMap.builder();
    int size = ProtocolUtils.readVarInt(buf);
    for (int i = 0; i < size; i++) {
      String key = ProtocolUtils.readString(buf);

      int innerSize = ProtocolUtils.readVarInt(buf);
      ImmutableMap.Builder<@NotNull String, int @NotNull []> innerBuilder = ImmutableMap.builder();
      for (int j = 0; j < innerSize; j++) {
        String innerKey = ProtocolUtils.readString(buf);
        int[] innerValue = ProtocolUtils.readVarIntArray(buf);
        innerBuilder.put(innerKey, innerValue);
      }

      builder.put(key, innerBuilder.build());
    }

    tags = builder.build();
  }

  /**
   * Encodes this tags update packet into the provided {@link ByteBuf}.
   *
   * <p>This writes the registry-to-tag structure by serializing each registry name,
   * followed by its associated tags and integer ID arrays.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, tags.size());
    for (Map.Entry<String, Map<String, int[]>> entry : tags.entrySet()) {
      ProtocolUtils.writeString(buf, entry.getKey());
      // Oh, joy
      ProtocolUtils.writeVarInt(buf, entry.getValue().size());
      for (Map.Entry<String, int[]> innerEntry : entry.getValue().entrySet()) {
        // Yeah, object oriented programming be damned
        ProtocolUtils.writeString(buf, innerEntry.getKey());
        ProtocolUtils.writeVarIntArray(buf, innerEntry.getValue());
      }
    }
  }

  /**
   * Handles this tags update packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates to {@code handler.handle(this)} to propagate updated tag metadata
   * to the session for compatibility or synchronization.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Provides an estimated number of bytes required to encode this tags update packet.
   *
   * <p>This calculation iterates through all registries and their associated tags,
   * summing the expected sizes of encoded strings, VarInt counts, and integer arrays.
   * The result approximates the total serialized size of the packet for efficient
   * buffer preallocation during encoding.</p>
   *
   * @param direction the packet direction (clientbound or serverbound)
   * @param version the Minecraft protocol version
   * @return the estimated encoded size of this packet in bytes
   */
  @Override
  public int encodeSizeHint(final Direction direction, final ProtocolVersion version) {
    int size = ProtocolUtils.varIntBytes(tags.size());
    for (Map.Entry<String, Map<String, int[]>> entry : tags.entrySet()) {
      size += ProtocolUtils.stringSizeHint(entry.getKey());
      size += ProtocolUtils.varIntBytes(entry.getValue().size());
      for (Map.Entry<String, int[]> innerEntry : entry.getValue().entrySet()) {
        size += ProtocolUtils.stringSizeHint(innerEntry.getKey());
        size += ProtocolUtils.varIntBytes(innerEntry.getValue().length);
        for (int innerEntryValue : innerEntry.getValue()) {
          size += ProtocolUtils.varIntBytes(innerEntryValue);
        }
      }
    }

    return size;
  }
}
