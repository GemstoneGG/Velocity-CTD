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
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a packet sent from the server to the client, containing custom report details.
 * This packet carries a map of key-value pairs, where each key and value are strings.
 */
public class ClientboundCustomReportDetailsPacket implements MinecraftPacket {

  /**
   * A map of detail keys to their associated string values.
   */
  private Map<String, String> details;

  /**
   * Constructs an empty {@code ClientboundCustomReportDetailsPacket} for decoding purposes.
   */
  public ClientboundCustomReportDetailsPacket() {
  }

  /**
   * Constructs a {@code ClientboundCustomReportDetailsPacket} with the given details map.
   *
   * @param details the map of key-value pairs to include in the packet
   */
  public ClientboundCustomReportDetailsPacket(final Map<String, String> details) {
    this.details = details;
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    int detailsCount = ProtocolUtils.readVarInt(buf);

    this.details = new HashMap<>(detailsCount);
    for (int i = 0; i < detailsCount; i++) {
      details.put(ProtocolUtils.readString(buf), ProtocolUtils.readString(buf));
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, details.size());

    details.forEach((key, detail) -> {
      ProtocolUtils.writeString(buf, key);
      ProtocolUtils.writeString(buf, detail);
    });
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Returns the map of custom report detail key-value pairs.
   *
   * @return the report details map
   */
  public Map<String, String> getDetails() {
    return details;
  }
}
