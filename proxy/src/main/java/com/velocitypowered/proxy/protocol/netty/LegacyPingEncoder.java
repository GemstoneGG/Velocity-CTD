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

package com.velocitypowered.proxy.protocol.netty;

import static io.netty.channel.ChannelHandler.Sharable;

import com.velocitypowered.proxy.protocol.packet.LegacyDisconnect;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Encodes {@code LegacyDisconnect} for Minecraft 1.3 - 1.6.4.
 */
@Sharable
public final class LegacyPingEncoder extends MessageToByteEncoder<LegacyDisconnect> {

  /**
   * The singleton instance of this encoder.
   */
  public static final LegacyPingEncoder INSTANCE = new LegacyPingEncoder();

  private LegacyPingEncoder() {
  }

  @Override
  protected void encode(final ChannelHandlerContext ctx, final LegacyDisconnect msg, final ByteBuf out) throws Exception {
    out.writeByte(0xff);
    writeLegacyString(out, msg.reason());
  }

  private static void writeLegacyString(final ByteBuf out, final String string) {
    out.writeShort(string.length());
    out.writeCharSequence(string, StandardCharsets.UTF_16BE);
  }
}
