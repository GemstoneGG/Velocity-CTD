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

package com.velocitypowered.proxy.protocol.packet.config;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.protocol.util.DeferredByteBufHolder;
import io.netty.buffer.ByteBuf;

/**
 * A server-to-client packet containing the server's code of conduct.
 *
 * <p>This packet is sent during the configuration stage to present the
 * server-defined conduct rules to the client. The client may later
 * respond with a {@link CodeOfConductAcceptPacket} to indicate
 * acceptance.</p>
 */
public class CodeOfConductPacket extends DeferredByteBufHolder implements MinecraftPacket {

  /**
   * Constructs a new {@code CodeOfConductPacket}.
   *
   * <p>Initializes with no content until decoded.</p>
   */
  public CodeOfConductPacket() {
    super(null);
  }

  /**
   * Decodes the code of conduct content from the provided buffer.
   *
   * @param buf the buffer containing the packet data
   * @param direction the packet direction
   * @param protocolVersion the protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    this.replace(buf.readRetainedSlice(buf.readableBytes()));
  }

  /**
   * Encodes the code of conduct content into the provided buffer.
   *
   * @param buf the target buffer
   * @param direction the packet direction
   * @param protocolVersion the protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    buf.writeBytes(this.content());
  }

  /**
   * Dispatches this packet to the given session handler.
   *
   * @param handler the session handler
   * @return {@code true} if the packet was handled
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
