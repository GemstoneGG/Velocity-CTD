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

import static com.velocitypowered.proxy.protocol.netty.MinecraftVarintLengthEncoder.IS_JAVA_CIPHER;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.netty.data.CompressedPacket;
import com.velocitypowered.proxy.protocol.netty.data.IdentifiedPacket;
import com.velocitypowered.proxy.protocol.netty.data.UncompressedPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.DataFormatException;

/**
 * Handler for compressing Minecraft packets.
 */
public class MinecraftCompressorAndLengthEncoder extends MessageToByteEncoder<IdentifiedPacket> {

  /**
   * The compression threshold. Packets smaller than this will not be compressed.
   */
  private int threshold;

  /**
   * The {@link VelocityCompressor} used to compress packets.
   */
  private final VelocityCompressor compressor;

  /**
   * Constructs a new {@code MinecraftCompressorAndLengthEncoder}.
   *
   * @param threshold  the compression threshold
   * @param compressor the compressor to use
   */
  public MinecraftCompressorAndLengthEncoder(final int threshold, final VelocityCompressor compressor) {
    this.threshold = threshold;
    this.compressor = compressor;
  }

  /**
   * Compresses and length-prefixes the packet according to the configured threshold.
   *
   * <p>If the packet is smaller than the threshold, or compression is disabled
   * (threshold ≤ 0), it is written uncompressed with a compressed-length marker of {@code 0}.
   * Otherwise, the packet is written in compressed form with its uncompressed size
   * varInt prefixed before the compressed bytes.</p>
   *
   * <p>When {@code msg} is an {@link UncompressedPacket}, the encoder may compress it
   * in-place if it meets the threshold. When {@code msg} is a {@link CompressedPacket},
   * the encoder forwards the compressed bytes if the uncompressed length meets the threshold,
   * or transparently emits the decompressed bytes if it does not.</p>
   *
   * @param ctx the Netty channel context
   * @param msg the identified packet (compressed or uncompressed)
   * @param out the output buffer to write the encoded frame to
   * @throws Exception if compression or decompression fails
   */
  @Override
  protected void encode(final ChannelHandlerContext ctx, final IdentifiedPacket msg, final ByteBuf out) throws Exception {
    if (msg instanceof UncompressedPacket uncompressed) {
      int uncompressedLength = uncompressed.getPacketBuf().readableBytes();
      if (uncompressedLength < threshold || threshold <= 0) {
        // Under the threshold, there is nothing to do.
        ProtocolUtils.writeVarInt(out, uncompressedLength + 1);
        ProtocolUtils.writeVarInt(out, 0);
        out.writeBytes(uncompressed.getPacketBuf());
        uncompressed.getPacketBuf().release();
      } else {
        handleCompressed(ctx, uncompressed, out);
      }
    } else if (msg instanceof CompressedPacket compressed) {
      if (compressed.getUncompressedLength() < threshold || threshold <= 0) {
        ProtocolUtils.writeVarInt(out, compressed.getUncompressedLength() + 1);
        ProtocolUtils.writeVarInt(out, 0);
        ByteBuf decompressed = compressed.decompress(ctx.alloc());
        out.writeBytes(decompressed);
        decompressed.release();
      } else {
        ProtocolUtils.writeVarInt(out, compressed.getCompressedBuf().readableBytes()
            + ProtocolUtils.varIntBytes(compressed.getUncompressedLength()));
        ProtocolUtils.writeVarInt(out, compressed.getUncompressedLength());
        out.writeBytes(compressed.getCompressedBuf());
        compressed.getCompressedBuf().release();
      }
    }
  }

  private void handleCompressed(final ChannelHandlerContext ctx, final UncompressedPacket msg, final ByteBuf out) throws DataFormatException {
    int uncompressed = msg.getPacketBuf().readableBytes();

    ProtocolUtils.write21BitVarInt(out, 0); // Stub packet length
    ProtocolUtils.writeVarInt(out, uncompressed);

    msg.compress(this.compressor, ctx.alloc(), out);

    int writerIndex = out.writerIndex();
    int packetLength = out.readableBytes() - 3;
    out.writerIndex(0);
    ProtocolUtils.write21BitVarInt(out, packetLength); // Rewrite packet length
    out.writerIndex(writerIndex);
  }

  /**
   * Allocates a new output buffer sized for the packet.
   *
   * <p>If the uncompressed length is below the threshold, a small buffer is allocated
   * to hold the raw data plus length prefixes. If compression is used, a larger buffer
   * is allocated based on the uncompressed size and expected overhead.</p>
   *
   * @param ctx the Netty channel context
   * @param msg the identified packet
   * @param preferDirect whether to prefer a direct buffer
   * @return the allocated buffer
   */
  @Override
  protected ByteBuf allocateBuffer(final ChannelHandlerContext ctx, final IdentifiedPacket msg,
                                   final boolean preferDirect) {
    int uncompressed;
    if (msg instanceof UncompressedPacket uncompressedPacket) {
      uncompressed = uncompressedPacket.getPacketBuf().readableBytes();
    } else if (msg instanceof CompressedPacket compressedPacket) {
      uncompressed = compressedPacket.getUncompressedLength();
    } else {
      throw new IllegalArgumentException("Unsupported identified packet type.");
    }

    if (uncompressed < threshold) {
      int finalBufferSize = uncompressed + 1;
      finalBufferSize += ProtocolUtils.varIntBytes(finalBufferSize);
      return IS_JAVA_CIPHER
          ? ctx.alloc().heapBuffer(finalBufferSize)
          : ctx.alloc().directBuffer(finalBufferSize);
    }

    // (maximum data length after compression) + packet length varInt + uncompressed data varInt
    int initialBufferSize = (uncompressed - 1) + 3 + ProtocolUtils.varIntBytes(uncompressed);
    return MoreByteBufUtils.preferredBuffer(ctx.alloc(), compressor, initialBufferSize);
  }

  /**
   * Invoked when the encoder is removed from the Netty pipeline.
   *
   * <p>Closes the associated {@link VelocityCompressor} to release native resources.</p>
   *
   * @param ctx the Netty channel context
   */
  @Override
  public void handlerRemoved(final ChannelHandlerContext ctx) {
    compressor.close();
  }

  /**
   * Updates the compression threshold.
   *
   * @param threshold the new threshold value
   */
  public void setThreshold(final int threshold) {
    this.threshold = threshold;
  }
}
