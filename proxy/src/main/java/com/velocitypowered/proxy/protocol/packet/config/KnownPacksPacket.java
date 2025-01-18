/*
 * Copyright (C) 2024 Velocity Contributors
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

import java.util.Arrays;
import java.util.Objects;

/**
 * The {@code KnownPacksPacket} class represents a packet that handles the synchronization
 * of known resource packs between the client and server in the Minecraft protocol.
 *
 * <p>This packet contains a list of {@link KnownPack} instances, each representing a resource
 * pack with a namespace, identifier, and version. It allows the server to inform the client
 * about available resource packs.</p>
 */
public class KnownPacksPacket implements MinecraftPacket {

  private static final int MAX_LENGTH_PACKS = Integer.getInteger("velocity.max-known-packs", 64);
  private static final QuietDecoderException TOO_MANY_PACKS =
      new QuietDecoderException("too many known packs");

  private KnownPack[] packs;

  public KnownPacksPacket() {
    packs = new KnownPack[0];
  }

  public KnownPacksPacket(final KnownPack[] packs) {
    this.packs = packs;
  }

  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
      final ProtocolVersion protocolVersion) {
    final int packCount = ProtocolUtils.readVarInt(buf);
    if (packCount > MAX_LENGTH_PACKS) {
      throw TOO_MANY_PACKS;
    }

    final KnownPack[] packs = new KnownPack[packCount];

    for (int i = 0; i < packCount; i++) {
      packs[i] = KnownPack.read(buf);
    }

    this.packs = packs;
  }

  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
      final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, packs.length);

    for (KnownPack pack : packs) {
      pack.write(buf);
    }
  }

  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  public KnownPack[] getPacks() {
    return packs;
  }

  @Override
  public String toString() {
    return "KnownPacksPacket{"
      + "packs=" + Arrays.toString(packs)
      + '}';
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

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      KnownPack knownPack = (KnownPack) o;
      return Objects.equals(id, knownPack.id) && Objects.equals(version, knownPack.version) && Objects.equals(namespace, knownPack.namespace);
    }

    @Override
    public int hashCode() {
      return Objects.hash(namespace, id, version);
    }

    @Override
    public String toString() {
      return "KnownPack{"
        + "namespace='" + namespace + '\''
        + ", id='" + id + '\''
        + ", version='" + version + '\''
        + '}';
    }
  }
}
