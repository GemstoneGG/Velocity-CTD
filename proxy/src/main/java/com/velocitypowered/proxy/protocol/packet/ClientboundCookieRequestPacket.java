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
import net.kyori.adventure.key.Key;

/**
 * Represents a packet sent from the server to the client to request cookies.
 * This packet can be used to initiate a request for cookie-related data from the client,
 * typically for authentication or tracking purposes.
 */
public class ClientboundCookieRequestPacket implements MinecraftPacket {

  /**
   * The key representing the requested cookie type.
   */
  private Key key;

  /**
   * Returns the {@link Key} for the cookie being requested.
   *
   * @return the cookie key
   */
  public Key getKey() {
    return key;
  }

  /**
   * Creates an empty cookie request packet.
   * This constructor is typically used during deserialization.
   */
  public ClientboundCookieRequestPacket() {
  }

  /**
   * Constructs a new {@code ClientboundCookieRequestPacket} with the specified cookie key.
   *
   * @param key the {@link Key} representing the cookie to request from the client
   */
  public ClientboundCookieRequestPacket(final Key key) {
    this.key = key;
  }

  @Override
  public final void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    this.key = ProtocolUtils.readKey(buf);
  }

  @Override
  public final void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeKey(buf, key);
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
