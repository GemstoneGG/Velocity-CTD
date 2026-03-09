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

import com.google.common.collect.Lists;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * Represents a packet sent to remove player information from the player list.
 * The packet contains a collection of {@link UUID}s representing the profiles to be removed.
 */
public class RemovePlayerInfoPacket implements MinecraftPacket {

  /**
   * The collection of player profile UUIDs to remove from the player list.
   */
  private Collection<UUID> profilesToRemove;

  /**
   * Constructs an empty {@code RemovePlayerInfoPacket}.
   * This constructor initializes the internal collection to an empty list.
   */
  public RemovePlayerInfoPacket() {
    this.profilesToRemove = new ArrayList<>();
  }

  /**
   * Constructs a {@code RemovePlayerInfoPacket} with the specified profiles to remove.
   *
   * @param profilesToRemove the collection of player UUIDs to remove from the player list
   */
  public RemovePlayerInfoPacket(final Collection<UUID> profilesToRemove) {
    this.profilesToRemove = profilesToRemove;
  }

  /**
   * Gets the collection of player profile UUIDs to remove from the player list.
   *
   * @return the collection of UUIDs
   */
  public Collection<UUID> getProfilesToRemove() {
    return profilesToRemove;
  }

  /**
   * Sets the collection of player profile UUIDs to remove from the player list.
   *
   * @param profilesToRemove the collection of UUIDs
   */
  public void setProfilesToRemove(final Collection<UUID> profilesToRemove) {
    this.profilesToRemove = profilesToRemove;
  }

  /**
   * Decodes the {@code RemovePlayerInfoPacket} from the provided {@link ByteBuf}.
   *
   * <p>This method reads a list of UUIDs representing player profiles that should
   * be removed from the tab list, based on the expected count prefix.</p>
   *
   * @param buf the byte buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the protocol version in use
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    int length = ProtocolUtils.readVarInt(buf);
    Collection<UUID> profilesToRemove = Lists.newArrayListWithCapacity(length);
    for (int idx = 0; idx < length; idx++) {
      profilesToRemove.add(ProtocolUtils.readUuid(buf));
    }

    this.profilesToRemove = profilesToRemove;
  }

  /**
   * Encodes this {@code RemovePlayerInfoPacket} into the provided {@link ByteBuf}.
   *
   * <p>This writes the number of UUIDs followed by each player profile UUID
   * that should be removed from the tab list.</p>
   *
   * @param buf the byte buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the protocol version in use
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, this.profilesToRemove.size());
    for (UUID uuid : this.profilesToRemove) {
      ProtocolUtils.writeUuid(buf, uuid);
    }
  }

  /**
   * Handles this packet using the provided {@link MinecraftSessionHandler}.
   *
   * <p>Delegates the handling of this packet to the session handler's
   * {@code handle(RemovePlayerInfoPacket)} method.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully; {@code false} otherwise
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
