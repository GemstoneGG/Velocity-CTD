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

package com.velocitypowered.proxy.protocol.netty;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes {@link MinecraftPacket} instances.
 */
public class MinecraftEncoder extends MessageToByteEncoder<MinecraftPacket> {

  /**
   * The direction this encoder is targeting.
   *
   * <p>This determines whether packets are being encoded for the
   * {@link ProtocolUtils.Direction#CLIENTBOUND} or {@link ProtocolUtils.Direction#SERVERBOUND}
   * direction.</p>
   */
  private final ProtocolUtils.Direction direction;

  /**
   * The current connection state this encoder is operating under.
   *
   * <p>This affects the set of packets that are valid to send, and how they
   * are serialized to the output buffer.</p>
   */
  private StateRegistry state;

  /**
   * The active protocol registry used for encoding packet IDs and resolving
   * version-specific serialization behaviors.
   *
   * <p>This registry is derived from the current {@link #state} and {@link #direction}
   * for a given {@link ProtocolVersion}.</p>
   */
  private StateRegistry.PacketRegistry.ProtocolRegistry registry;

  /**
   * Creates a new {@code MinecraftEncoder} encoding packets for the specified {@code direction}.
   *
   * @param direction the direction to encode to
   */
  public MinecraftEncoder(final ProtocolUtils.Direction direction) {
    this.direction = Preconditions.checkNotNull(direction, "direction");
    this.registry = StateRegistry.HANDSHAKE.getProtocolRegistry(direction, ProtocolVersion.MINIMUM_VERSION);
    this.state = StateRegistry.HANDSHAKE;
  }

  @Override
  protected final void encode(final ChannelHandlerContext ctx, final MinecraftPacket msg, final ByteBuf out) {
    int packetId = this.registry.getPacketId(msg);
    ProtocolUtils.writeVarInt(out, packetId);
    msg.encode(out, direction, registry.version);
  }

  /**
   * Updates the protocol version used by this encoder.
   *
   * <p>This method re-initializes the protocol registry to match the given version
   * under the current {@link #state} and {@link #direction}.</p>
   *
   * @param protocolVersion the new protocol version to encode against
   */
  public void setProtocolVersion(final ProtocolVersion protocolVersion) {
    this.registry = state.getProtocolRegistry(direction, protocolVersion);
  }

  /**
   * Updates the connection state used by this encoder.
   *
   * <p>This method also resets the protocol registry using the current version from
   * the previous registry and the newly provided state.</p>
   *
   * @param state the new connection state
   */
  public void setState(final StateRegistry state) {
    this.state = state;
    this.setProtocolVersion(registry.version);
  }

  /**
   * Gets the direction this encoder is targeting.
   *
   * @return the encoder's target direction
   */
  public ProtocolUtils.Direction getDirection() {
    return direction;
  }
}
