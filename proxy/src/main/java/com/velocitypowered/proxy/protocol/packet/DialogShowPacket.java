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
import com.velocitypowered.proxy.protocol.StateRegistry;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;

/**
 * Represents the packet sent by the server to the client to display a configuration dialog
 * during the configuration phase in Minecraft 1.21.6+.
 *
 * <p>This packet is only relevant in the CONFIG and PLAY states. If the ID is {@code 0},
 * a dialog is to be shown and the accompanying {@link BinaryTag} contains its data.</p>
 */
public class DialogShowPacket implements MinecraftPacket {

  /**
   * The state registry associated with the current protocol phase.
   * Used to determine whether the packet is being used in CONFIG state or not.
   */
  private final StateRegistry state;

  /**
   * The dialog ID.
   * If {@code 0}, a dialog should be displayed and {@link #nbt} will contain the dialog contents.
   */
  private int id;

  /**
   * The NBT data representing the dialog content.
   * This is only present if {@link #id} is {@code 0}.
   */
  private BinaryTag nbt;

  /**
   * Constructs a new DialogShowPacket for the specified protocol state.
   *
   * @param state the state registry representing the current protocol phase
   */
  public DialogShowPacket(final StateRegistry state) {
    this.state = state;
  }

  /**
   * Decodes the dialog packet from the given buffer.
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (SERVERBOUND or CLIENTBOUND)
   * @param protocolVersion the current protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    this.id = this.state == StateRegistry.CONFIG ? 0 : ProtocolUtils.readVarInt(buf);
    if (this.id == 0) {
      this.nbt = ProtocolUtils.readBinaryTag(buf, protocolVersion, BinaryTagIO.reader());
    }
  }

  /**
   * Encodes the dialog packet into the given buffer.
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (SERVERBOUND or CLIENTBOUND)
   * @param protocolVersion the current protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    if (this.state == StateRegistry.CONFIG) {
      ProtocolUtils.writeBinaryTag(buf, protocolVersion, this.nbt);
    } else {
      ProtocolUtils.writeVarInt(buf, this.id);
      if (this.id == 0) {
        ProtocolUtils.writeBinaryTag(buf, protocolVersion, this.nbt);
      }
    }
  }

  /**
   * Handles this packet using the given session handler.
   *
   * @param handler the Minecraft session handler
   * @return true if the packet was handled, false otherwise
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
