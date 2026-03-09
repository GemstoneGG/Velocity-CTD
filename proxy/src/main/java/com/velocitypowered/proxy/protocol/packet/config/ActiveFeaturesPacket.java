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

package com.velocitypowered.proxy.protocol.packet.config;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;

/**
 * The {@code ActiveFeaturesPacket} class represents a packet that communicates the currently
 * active features between the client and server in the Minecraft protocol.
 *
 * <p>This packet is used to inform the client about which features are enabled or active,
 * potentially based on server configurations or gameplay states.</p>
 */
public class ActiveFeaturesPacket implements MinecraftPacket {

  /**
   * The list of active feature identifiers, sent as {@link Key} entries.
   */
  private Key[] activeFeatures;

  /**
   * Constructs an {@code ActiveFeaturesPacket} with a specific set of feature keys.
   *
   * @param activeFeatures the array of enabled feature keys
   */
  public ActiveFeaturesPacket(final Key[] activeFeatures) {
    this.activeFeatures = activeFeatures;
  }

  /**
   * Constructs an empty {@code ActiveFeaturesPacket}, typically for decoding.
   */
  public ActiveFeaturesPacket() {
    this.activeFeatures = new Key[0];
  }

  /**
   * Updates the list of active features in this packet.
   *
   * @param activeFeatures the new feature keys to set
   */
  public void setActiveFeatures(final Key[] activeFeatures) {
    this.activeFeatures = activeFeatures;
  }

  /**
   * Returns the array of feature keys currently declared as active.
   *
   * @return the active feature list
   */
  public Key[] getActiveFeatures() {
    return activeFeatures;
  }

  /**
   * Decodes this active features packet from the provided {@link ByteBuf}.
   *
   * <p>This reads an array of {@link Key} entries representing the currently enabled
   * or advertised feature flags.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    activeFeatures = ProtocolUtils.readKeyArray(buf);
  }

  /**
   * Encodes this active features packet into the provided {@link ByteBuf}.
   *
   * <p>This writes the array of {@link Key} entries representing feature flags
   * to inform the client of the server's capabilities.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                     final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeKeyArray(buf, activeFeatures);
  }

  /**
   * Handles this active features packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates handling to {@code handler.handle(this)} so the session can react
   * to the active feature set provided by the packet.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
