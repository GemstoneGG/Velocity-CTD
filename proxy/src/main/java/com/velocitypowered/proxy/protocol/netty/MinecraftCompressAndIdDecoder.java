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

import static com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible;
import static com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer;
import static com.velocitypowered.proxy.protocol.util.NettyPreconditions.checkFrame;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.network.limiter.PacketLimiter;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.netty.data.CompressedPacket;
import com.velocitypowered.proxy.protocol.netty.data.UncompressedPacket;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Decompresses inbound Minecraft packets and extracts their packet ids in a single pipeline
 * stage. Replaces the older {@code MinecraftCompressDecoder}.
 */
public class MinecraftCompressAndIdDecoder extends MessageToMessageDecoder<ByteBuf> {

  private static final int SERVERBOUND_MAXIMUM_UNCOMPRESSED_SIZE = 2 * 1024 * 1024; // 2MiB
  private static final int VANILLA_MAXIMUM_UNCOMPRESSED_SIZE = 8 * 1024 * 1024; // 8MiB
  private static final int HARD_MAXIMUM_UNCOMPRESSED_SIZE = 128 * 1024 * 1024; // 128MiB

  private static final int CLIENTBOUND_UNCOMPRESSED_CAP =
      Boolean.getBoolean("velocity.increased-compression-cap")
          ? HARD_MAXIMUM_UNCOMPRESSED_SIZE : VANILLA_MAXIMUM_UNCOMPRESSED_SIZE;

  private static final int SERVERBOUND_UNCOMPRESSED_CAP =
      Boolean.getBoolean("velocity.increased-compression-cap")
          ? HARD_MAXIMUM_UNCOMPRESSED_SIZE : SERVERBOUND_MAXIMUM_UNCOMPRESSED_SIZE;

  private static final boolean SKIP_COMPRESSION_VALIDATION =
      Boolean.getBoolean("velocity.skip-uncompressed-packet-size-validation");

  private final ProtocolUtils.Direction direction;
  private final VelocityServer server;
  private int threshold;
  private @Nullable VelocityCompressor compressor;
  private @Nullable VelocityCompressor javaCompressor;
  private @Nullable PacketLimiter packetLimiter;

  /**
   * Creates a decoder for the pre-compression handshake phase. Compression is disabled
   * (threshold zero) until {@link #setCompression} promotes it.
   *
   * @param direction the direction of the packets being decoded
   * @param server    the proxy server, used to read the configured decompression threshold
   */
  public MinecraftCompressAndIdDecoder(ProtocolUtils.Direction direction, VelocityServer server) {
    this.direction = direction;
    this.server = server;
    this.threshold = 0;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    if (threshold <= 0 || compressor == null) {
      // Compression not yet enabled on this connection: the wire payload is the raw packet
      // body and the entire buffer is the packet, packet id varint first.
      emitUncompressed(in.retain(), out);
      return;
    }

    int claimedUncompressedSize = ProtocolUtils.readVarInt(in);

    if (claimedUncompressedSize == 0) {
      // Sender chose not to compress this packet (it was below their threshold).
      if (!SKIP_COMPRESSION_VALIDATION) {
        int actualUncompressedSize = in.readableBytes();
        checkFrame(actualUncompressedSize < threshold, "Actual uncompressed size %s is greater than"
            + " threshold %s", actualUncompressedSize, threshold);
      }
      if (packetLimiter != null && !packetLimiter.account(in.readableBytes())) {
        throw new QuietDecoderException("Rate limit exceeded while processing packets for %s"
            .formatted(ctx.channel().remoteAddress()));
      }
      emitUncompressed(in.retain(), out);
      return;
    }

    checkFrame(claimedUncompressedSize >= threshold, "Uncompressed size %s is less than"
        + " threshold %s", claimedUncompressedSize, threshold);
    int cap = direction == ProtocolUtils.Direction.CLIENTBOUND
        ? CLIENTBOUND_UNCOMPRESSED_CAP : SERVERBOUND_UNCOMPRESSED_CAP;
    checkFrame(claimedUncompressedSize <= cap,
        "Uncompressed size %s exceeds hard threshold of %s", claimedUncompressedSize, cap);

    int decompressionThreshold = server.getConfiguration().getDecompressionThreshold();
    boolean canPassThrough = javaCompressor != null
        && decompressionThreshold > 0
        && claimedUncompressedSize >= decompressionThreshold;

    if (!canPassThrough) {
      ByteBuf compatibleIn = ensureCompatible(ctx.alloc(), compressor, in);
      ByteBuf uncompressed = preferredBuffer(ctx.alloc(), compressor, claimedUncompressedSize);
      try {
        compressor.inflate(compatibleIn, uncompressed, claimedUncompressedSize);
        checkFrame(uncompressed.writerIndex() == claimedUncompressedSize,
            "Decompressed size %s does not match claimed uncompressed size %s",
            uncompressed.writerIndex(), claimedUncompressedSize);
        if (packetLimiter != null && !packetLimiter.account(claimedUncompressedSize)) {
          throw new QuietDecoderException("Rate limit exceeded while processing packets for %s"
              .formatted(ctx.channel().remoteAddress()));
        }
        emitUncompressed(uncompressed, out);
      } catch (Exception e) {
        uncompressed.release();
        throw e;
      } finally {
        compatibleIn.release();
      }
      return;
    }

    if (packetLimiter != null && !packetLimiter.account(claimedUncompressedSize)) {
      throw new QuietDecoderException("Rate limit exceeded while processing packets for %s"
          .formatted(ctx.channel().remoteAddress()));
    }
    ByteBuf packetIdBuf = preferredBuffer(ctx.alloc(), javaCompressor, 5);
    try {
      javaCompressor.inflatePartial(in, packetIdBuf, 5);
      int packetId = ProtocolUtils.readVarInt(packetIdBuf);
      out.add(new CompressedPacket(packetId, claimedUncompressedSize, in.retain(), compressor));
    } finally {
      packetIdBuf.release();
    }
  }

  private static void emitUncompressed(ByteBuf buf, List<Object> out) {
    int originalReaderIndex = buf.readerIndex();
    int packetId = ProtocolUtils.readVarInt(buf);
    buf.readerIndex(originalReaderIndex);
    out.add(new UncompressedPacket(packetId, buf));
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) {
    if (compressor != null) {
      compressor.close();
      compressor = null;
    }

    if (javaCompressor != null) {
      javaCompressor.close();
      javaCompressor = null;
    }
  }

  /**
   * Promotes the decoder out of the pre-compression handshake state, wiring it with the
   * compressors it needs. May also be called with {@code threshold == 0} and null compressors
   * to demote the decoder back to the no-compression wrapping path; any previously installed
   * compressors are closed.
   *
   * @param threshold      the configured compression threshold
   * @param compressor     the preferred compressor for full inflations, or {@code null}
   * @param javaCompressor a compressor that supports {@link VelocityCompressor#inflatePartial},
   *                       or {@code null} to disable the pass-through path
   */
  public void setCompression(int threshold, @Nullable VelocityCompressor compressor,
                             @Nullable VelocityCompressor javaCompressor) {
    if (this.compressor != null && this.compressor != compressor) {
      this.compressor.close();
    }
    if (this.javaCompressor != null && this.javaCompressor != javaCompressor) {
      this.javaCompressor.close();
    }
    this.threshold = threshold;
    this.compressor = compressor;
    this.javaCompressor = javaCompressor;
  }

  public void setThreshold(int threshold) {
    this.threshold = threshold;
  }

  public void setPacketLimiter(@Nullable PacketLimiter packetLimiter) {
    this.packetLimiter = packetLimiter;
  }
}
