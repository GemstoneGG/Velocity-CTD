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

  @Override
  public final void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    if (buf.readBoolean()) {
      this.id = ProtocolUtils.readUuid(buf);
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    buf.writeBoolean(id != null);

    if (id != null) {
      ProtocolUtils.writeUuid(buf, id);
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
