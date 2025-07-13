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

import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent.Status;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Represents the response packet sent by the client after receiving a resource pack request from the server.
 * The packet contains information about the client's response, including the resource pack status.
 */
public class ResourcePackResponsePacket implements MinecraftPacket {

  /**
   * The unique identifier of the resource pack response.
   * Only used for Minecraft 1.20.3 and above.
   */
  private UUID id;

  /**
   * The SHA-1 hash of the resource pack.
   * Used by Minecraft 1.9.4 and below.
   */
  private String hash = "";

  /**
   * The status of the resource pack response sent by the client.
   */
  private @MonotonicNonNull Status status;

  /**
   * Constructs a new {@code ResourcePackResponsePacket}.
   */
  public ResourcePackResponsePacket() {
  }

  /**
   * Constructs a new {@code ResourcePackResponsePacket} with the specified parameters.
   *
   * @param id the unique identifier for the response
   * @param hash the hash of the resource pack
   * @param status the status of the resource pack
   */
  public ResourcePackResponsePacket(final UUID id, final String hash, @MonotonicNonNull final Status status) {
    this.id = id;
    this.hash = hash;
    this.status = status;
  }

  /**
   * Gets the status of the resource pack response.
   *
   * @return the status of the response
   * @throws IllegalStateException if the packet has not been deserialized yet
   */
  public Status getStatus() {
    if (status == null) {
      throw new IllegalStateException("Packet not yet deserialized");
    }

    return status;
  }

  /**
   * Returns the SHA-1 hash of the resource pack.
   *
   * @return the hash string
   */
  public String getHash() {
    return hash;
  }

  /**
   * Returns the UUID of the resource pack response.
   *
   * @return the UUID
   */
  public UUID getId() {
    return id;
  }

  @Override
  public final void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      this.id = ProtocolUtils.readUuid(buf);
    }

    if (protocolVersion.noGreaterThan(ProtocolVersion.MINECRAFT_1_9_4)) {
      this.hash = ProtocolUtils.readString(buf);
    }

    this.status = Status.values()[ProtocolUtils.readVarInt(buf)];
  }

  @Override
  public final void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      ProtocolUtils.writeUuid(buf, id);
    }

    if (protocolVersion.noGreaterThan(ProtocolVersion.MINECRAFT_1_9_4)) {
      ProtocolUtils.writeString(buf, hash);
    }

    ProtocolUtils.writeVarInt(buf, status.ordinal());
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public final String toString() {
    return "ResourcePackResponsePacket{"
        + "id=" + id
        + ", hash='" + hash + '\''
        + ", status=" + status
        + '}';
  }
}
