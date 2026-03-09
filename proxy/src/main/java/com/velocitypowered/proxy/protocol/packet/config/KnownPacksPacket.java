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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;

/**
 * The {@code KnownPacksPacket} class represents a packet that handles the synchronization
 * of known resource packs between the client and server in the Minecraft protocol.
 *
 * <p>This packet contains a list of {@link KnownPack} instances, each representing a resource
 * pack with a namespace, identifier, and version. It allows the server to inform the client
 * about available resource packs.</p>
 */
public class KnownPacksPacket implements MinecraftPacket {

  /**
   * The maximum number of known packs allowed in a serverbound packet.
   *
   * <p>This limit is controlled by the {@code velocity.max-known-packs} system property
   * (defaults to 64) to prevent abuse or protocol overflows.</p>
   */
  private static final int MAX_LENGTH_PACKS = Integer.getInteger("velocity.max-known-packs", 64);

  /**
   * Thrown when too many packs are received in a serverbound packet.
   */
  private static final QuietDecoderException TOO_MANY_PACKS = new QuietDecoderException("too many known packs");

  /**
   * The array of known resource packs being synchronized.
   */
  private KnownPack[] packs;

  /**
   * Decodes this known packs packet from the provided {@link ByteBuf}.
   *
   * <p>This reads a list of known resource packs, each with a namespace, ID, and version.
   * If the packet is serverbound and exceeds the configured maximum limit, it throws
   * a {@link QuietDecoderException}.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   * @throws QuietDecoderException if too many packs are received in a serverbound context
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    final int packCount = ProtocolUtils.readVarInt(buf);
    if (direction == ProtocolUtils.Direction.SERVERBOUND && packCount > MAX_LENGTH_PACKS) {
      throw TOO_MANY_PACKS;
    }

    final KnownPack[] packs = new KnownPack[packCount];

    for (int i = 0; i < packCount; i++) {
      packs[i] = KnownPack.read(buf);
    }

    this.packs = packs;
  }

  /**
   * Encodes this known packs packet into the given {@link ByteBuf}.
   *
   * <p>This writes all known packs (namespace, ID, version) into the buffer
   * using the current protocol version format.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, packs.length);

    for (KnownPack pack : packs) {
      pack.write(buf);
    }
  }

  /**
   * Handles this known packs packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates handling to {@code handler.handle(this)} to sync known resource
   * packs with the server session.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * The {@code KnownPack} record represents a known resource pack with a namespace,
   * identifier, and version in the Minecraft protocol.
   *
   * <p>It encapsulates the information needed to identify a resource pack, typically used
   * for managing or synchronizing resource packs between the client and server.</p>
   *
   * @param namespace the namespace of the resource pack (e.g., "minecraft" or a mod name)
   * @param id the unique identifier of the resource pack within the namespace
   * @param version the version of the resource pack
   */
  public record KnownPack(String namespace, String id, String version) {
    private static KnownPack read(final ByteBuf buf) {
      return new KnownPack(ProtocolUtils.readString(buf), ProtocolUtils.readString(buf), ProtocolUtils.readString(buf));
    }

    private void write(final ByteBuf buf) {
      ProtocolUtils.writeString(buf, namespace);
      ProtocolUtils.writeString(buf, id);
      ProtocolUtils.writeString(buf, version);
    }
  }
}
