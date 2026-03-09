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

package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;
import java.util.UUID;

/**
 * Represents a packet sent to remove a previously applied resource pack from the client.
 * The packet contains an optional UUID that identifies the resource pack to be removed.
 */
public class RemoveResourcePackPacket implements MinecraftPacket {

  /**
   * The UUID of the resource pack to remove.
   * May be {@code null} if no specific resource pack is being targeted.
   */
  private UUID id;

  /**
   * Constructs an empty {@code RemoveResourcePackPacket}.
   * The UUID will be {@code null} until decoded or explicitly set.
   */
  public RemoveResourcePackPacket() {
  }

  /**
   * Constructs a {@code RemoveResourcePackPacket} with the specified resource pack UUID.
   *
   * @param id the UUID of the resource pack to remove
   */
  public RemoveResourcePackPacket(final UUID id) {
    this.id = id;
  }

  /**
   * Gets the UUID of the resource pack to remove.
   *
   * @return the UUID of the resource pack, or {@code null} if not set
   */
  public UUID getId() {
    return id;
  }

  /**
   * Decodes this resource pack removal packet from the given {@link ByteBuf}.
   *
   * <p>This method reads a boolean flag indicating whether a UUID is present,
   * and then reads the UUID if applicable.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    if (buf.readBoolean()) {
      this.id = ProtocolUtils.readUuid(buf);
    }
  }

  /**
   * Encodes this resource pack removal packet into the given {@link ByteBuf}.
   *
   * <p>This method writes a boolean flag and the UUID (if present) identifying
   * the resource pack to be removed.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    buf.writeBoolean(id != null);

    if (id != null) {
      ProtocolUtils.writeUuid(buf, id);
    }
  }

  /**
   * Handles this resource pack removal packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to apply resource pack cleanup logic.</p>
   *
   * @param handler the session handler responsible for handling this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
