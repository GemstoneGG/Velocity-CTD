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
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a server-bound packet sent by the client containing a key and an optional payload.
 * This packet is typically used for exchanging metadata or other information between the client
 * and server.
 */
public class ServerboundCookieResponsePacket implements MinecraftPacket {

  /**
   * The key representing the type of cookie data.
   */
  private Key key;

  /**
   * The optional payload associated with the cookie, if any.
   */
  private byte @Nullable [] payload;

  /**
   * Returns the key associated with this cookie response.
   *
   * @return the {@link Key} of the cookie response
   */
  public Key getKey() {
    return key;
  }

  /**
   * Returns the optional payload included in this cookie response.
   *
   * @return the payload as a byte array, or {@code null} if none is present
   */
  public byte @Nullable [] getPayload() {
    return payload;
  }

  /**
   * Constructs an empty {@code ServerboundCookieResponsePacket}.
   *
   * <p>Fields must be populated manually before encoding.</p>
   */
  public ServerboundCookieResponsePacket() {
  }

  /**
   * Constructs a {@code ServerboundCookieResponsePacket} with the given key and optional payload.
   *
   * @param key the key associated with the cookie response
   * @param payload the optional payload, or {@code null} if not provided
   */
  public ServerboundCookieResponsePacket(final Key key, final byte @Nullable [] payload) {
    this.key = key;
    this.payload = payload;
  }

  @Override
  public final void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    this.key = ProtocolUtils.readKey(buf);
    if (buf.readBoolean()) {
      this.payload = ProtocolUtils.readByteArray(buf, 5120);
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeKey(buf, key);
    final boolean hasPayload = payload != null && payload.length > 0;
    buf.writeBoolean(hasPayload);
    if (hasPayload) {
      ProtocolUtils.writeByteArray(buf, payload);
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
