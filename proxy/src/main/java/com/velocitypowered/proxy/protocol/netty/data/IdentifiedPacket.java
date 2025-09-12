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

package com.velocitypowered.proxy.protocol.netty.data;

/**
 * Base class for packets that carry a numeric packet identifier.
 *
 * <p>The identifier corresponds to the protocol-specific packet id used by encoders
 * and decoders to route payloads.</p>
 */
public abstract class IdentifiedPacket {

  /**
   * The protocol packet identifier.
   */
  private final int packetId;

  /**
   * Creates a new identified packet.
   *
   * @param packetId the protocol packet identifier
   */
  public IdentifiedPacket(final int packetId) {
    this.packetId = packetId;
  }

  /**
   * Returns the protocol packet identifier.
   *
   * @return the packet id
   */
  public int getPacketId() {
    return this.packetId;
  }
}
