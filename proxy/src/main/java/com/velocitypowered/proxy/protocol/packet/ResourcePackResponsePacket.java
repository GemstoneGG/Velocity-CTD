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
  public ResourcePackResponsePacket(final UUID id, final String hash, final @MonotonicNonNull Status status) {
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

  /**
   * Decodes this resource pack response packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the UUID (1.20.3+), SHA-1 hash (1.9.4 and below), and the
   * {@link Status} representing the client's response to the resource pack prompt.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      this.id = ProtocolUtils.readUuid(buf);
    }

    if (protocolVersion.noGreaterThan(ProtocolVersion.MINECRAFT_1_9_4)) {
      this.hash = ProtocolUtils.readString(buf);
    }

    this.status = Status.values()[ProtocolUtils.readVarInt(buf)];
  }

  /**
   * Encodes this resource pack response packet into the provided {@link ByteBuf}.
   *
   * <p>This writes the UUID (if 1.20.3+), SHA-1 hash (if 1.9.4 and below),
   * and the ordinal value of the {@link Status} response.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      ProtocolUtils.writeUuid(buf, id);
    }

    if (protocolVersion.noGreaterThan(ProtocolVersion.MINECRAFT_1_9_4)) {
      ProtocolUtils.writeString(buf, hash);
    }

    ProtocolUtils.writeVarInt(buf, status.ordinal());
  }

  /**
   * Handles this resource pack response packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates packet processing to {@code handler.handle(this)} to handle
   * player responses to resource pack requests.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Returns a string representation of this resource pack response packet.
   *
   * <p>This includes the UUID, resource pack hash, and response status.</p>
   *
   * @return a string describing this packet
   */
  @Override
  public String toString() {
    return "ResourcePackResponsePacket{"
        + "id=" + id
        + ", hash='" + hash + '\''
        + ", status=" + status
        + '}';
  }
}
