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

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    int length = ProtocolUtils.readVarInt(buf);
    Collection<UUID> profilesToRemove = Lists.newArrayListWithCapacity(length);
    for (int idx = 0; idx < length; idx++) {
      profilesToRemove.add(ProtocolUtils.readUuid(buf));
    }

    this.profilesToRemove = profilesToRemove;
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, this.profilesToRemove.size());
    for (UUID uuid : this.profilesToRemove) {
      ProtocolUtils.writeUuid(buf, uuid);
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
