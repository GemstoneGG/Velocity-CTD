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
import io.netty.buffer.ByteBuf;

/**
 * Clientbound scoreboard "set objective" packet.
 *
 * <p>Only the leading {@code name} and {@code action} fields are decoded. Their layout has been stable since 1.8.
 */
public class ObjectivePacket implements MinecraftPacket {

  public static final byte ADD = 0;
  public static final byte REMOVE = 1;
  public static final byte UPDATE = 2;

  private static final byte[] EMPTY = new byte[0];

  private String name = "";
  private byte action;
  // Everything after the action byte, preserved unparsed so forwarding is version-agnostic.
  private byte[] rest = EMPTY;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public byte getAction() {
    return action;
  }

  public void setAction(byte action) {
    this.action = action;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    name = ProtocolUtils.readString(buf);
    action = buf.readByte();
    rest = new byte[buf.readableBytes()];
    buf.readBytes(rest);
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    ProtocolUtils.writeString(buf, name);
    buf.writeByte(action);
    buf.writeBytes(rest);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
