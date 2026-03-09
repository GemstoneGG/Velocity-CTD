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

import static com.velocitypowered.proxy.protocol.util.NettyPreconditions.checkFrame;

import com.velocitypowered.proxy.protocol.packet.LegacyHandshakePacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPingPacket;
import com.velocitypowered.proxy.protocol.packet.legacyping.LegacyMinecraftPingVersion;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Decodes Minecraft 1.3 - 1.6.4 server ping requests.
 */
public class LegacyPingDecoder extends ByteToMessageDecoder {

  /**
   * The expected channel name for Minecraft 1.6.x legacy pings.
   */
  private static final String MC_1_6_CHANNEL = "MC|PingHost";

  /**
   * Decodes incoming datagrams to detect and parse legacy Minecraft ping and handshake packets
   * from versions 1.3 to 1.6.4.
   *
   * <p>Supports:</p>
   * <ul>
   *   <li>1.3: Single 0xFE byte</li>
   *   <li>1.4–1.5: 0xFE 0x01</li>
   *   <li>1.6.x: 0xFE 0x01 + extended data tagged with {@code "MC|PingHost"}</li>
   *   <li>Legacy handshake: 0x02 prefix</li>
   * </ul>
   *
   * <p>If a matching packet format is detected, a corresponding {@link LegacyPingPacket}
   * or {@link LegacyHandshakePacket} is added to the output list and passed along the pipeline.
   * If the packet is not a legacy ping or handshake, this decoder removes itself from the pipeline
   * to avoid interfering with modern protocols.</p>
   *
   * @param ctx the Netty channel context
   * @param in the input buffer containing the received data
   * @param out the output list to which decoded packets are added
   * @throws Exception if packet parsing fails
   */
  @Override
  protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) throws Exception {
    if (!in.isReadable()) {
      return;
    }

    if (!ctx.channel().isActive()) {
      in.clear();
      return;
    }

    int originalReaderIndex = in.readerIndex();
    short first = in.readUnsignedByte();
    if (first == 0xfe) {
      // possibly a ping
      if (!in.isReadable()) {
        out.add(new LegacyPingPacket(LegacyMinecraftPingVersion.MINECRAFT_1_3));
        return;
      }

      short next = in.readUnsignedByte();
      if (next == 1 && !in.isReadable()) {
        out.add(new LegacyPingPacket(LegacyMinecraftPingVersion.MINECRAFT_1_4));
        return;
      }

      // We got a 1.6.x ping. Let's chomp off the stuff we don't need.
      out.add(readExtended16Data(in));
    } else if (first == 0x02 && in.isReadable()) {
      in.skipBytes(in.readableBytes());
      out.add(new LegacyHandshakePacket());
    } else {
      in.readerIndex(originalReaderIndex);
      ctx.pipeline().remove(this);
    }
  }

  private static LegacyPingPacket readExtended16Data(final ByteBuf in) {
    in.skipBytes(1);
    String channelName = readLegacyString(in);
    if (!channelName.equals(MC_1_6_CHANNEL)) {
      throw new IllegalArgumentException("Didn't find correct channel");
    }

    in.skipBytes(3);
    String hostname = readLegacyString(in);
    int port = in.readInt();

    return new LegacyPingPacket(LegacyMinecraftPingVersion.MINECRAFT_1_6, InetSocketAddress.createUnresolved(hostname, port));
  }

  private static String readLegacyString(final ByteBuf buf) {
    int len = buf.readShort() * Character.BYTES;
    checkFrame(buf.isReadable(len), "String length %s is too large for available bytes %d",
        len, buf.readableBytes());
    String str = buf.toString(buf.readerIndex(), len, StandardCharsets.UTF_16BE);
    buf.skipBytes(len);
    return str;
  }
}
