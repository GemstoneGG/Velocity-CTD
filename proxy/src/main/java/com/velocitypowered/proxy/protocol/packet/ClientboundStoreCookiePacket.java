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
import net.kyori.adventure.key.Key;

/**
 * Represents a packet sent from the server to the client to store a cookie.
 * This packet can be used to send cookie-related data from the server to be stored or processed
 * by the client.
 */
public class ClientboundStoreCookiePacket implements MinecraftPacket {

  /**
   * The {@link Key} identifying the type of cookie to be stored.
   */
  private Key key;

  /**
   * The payload associated with the cookie, stored as a byte array.
   */
  private byte[] payload;

  /**
   * Returns the {@link Key} identifying the type of cookie.
   *
   * @return the cookie key
   */
  public Key getKey() {
    return key;
  }

  /**
   * Returns the payload of the cookie as a byte array.
   *
   * @return the cookie payload
   */
  public byte[] getPayload() {
    return payload;
  }

  /**
   * Constructs an empty {@code ClientboundStoreCookiePacket}.
   * This constructor is primarily used during decoding.
   */
  public ClientboundStoreCookiePacket() {
  }

  /**
   * Constructs a new {@code ClientboundStoreCookiePacket} with the given key and payload.
   *
   * @param key the {@link Key} identifying the cookie
   * @param payload the byte array representing the cookie's payload
   */
  public ClientboundStoreCookiePacket(final Key key, final byte[] payload) {
    this.key = key;
    this.payload = payload;
  }

  /**
   * Decodes this store cookie packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the {@link Key} identifying the cookie and the associated payload
   * as a byte array.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    this.key = ProtocolUtils.readKey(buf);
    this.payload = ProtocolUtils.readByteArray(buf, 5120);
  }

  /**
   * Encodes this store cookie packet into the provided {@link ByteBuf}.
   *
   * <p>This writes the {@link Key} and the associated payload as a byte array.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeKey(buf, key);
    ProtocolUtils.writeByteArray(buf, payload);
  }

  /**
   * Handles this store cookie packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to store or process the
   * received cookie data.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
