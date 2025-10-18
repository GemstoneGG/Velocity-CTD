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
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.DataFormatException;

/**
 * Handler for compressing Minecraft packets.
 */
public class MinecraftCompressorAndLengthEncoder extends MessageToByteEncoder<ByteBuf> {

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
   * Compresses the given {@link ByteBuf} if it exceeds the configured compression threshold.
   *
   * <p>If the input is smaller than the threshold, the packet is written uncompressed with a 0 marker.
   * Otherwise, it is compressed and prefixed with its uncompressed size and compressed length.</p>
   *
   * @param ctx the Netty channel context
   * @param msg the uncompressed Minecraft packet
   * @param out the output buffer to write the encoded packet to
   * @throws Exception if compression fails
   */
  @Override
  protected void encode(final ChannelHandlerContext ctx, final ByteBuf msg, final ByteBuf out) throws Exception {
    int uncompressed = msg.readableBytes();
    if (uncompressed < threshold) {
      // Under the threshold, there is nothing to do.
      ProtocolUtils.writeVarInt(out, uncompressed + 1);
      out.writeByte(0);
      out.writeBytes(msg);
    } else {
      handleCompressed(ctx, msg, out);
    }
  }

  private void handleCompressed(final ChannelHandlerContext ctx, final ByteBuf msg, final ByteBuf out) throws DataFormatException {
    int uncompressed = msg.readableBytes();

    out.writeMedium(0); // Reserve the packet length
    ProtocolUtils.writeVarInt(out, uncompressed);
    ByteBuf compatibleIn = MoreByteBufUtils.ensureCompatible(ctx.alloc(), compressor, msg);

    int startCompressed = out.writerIndex();
    try {
      compressor.deflate(compatibleIn, out);
    } finally {
      compatibleIn.release();
    }

    int compressedLength = out.writerIndex() - startCompressed;
    if (compressedLength >= 1 << 21) {
      throw new DataFormatException("The server sent a very large (over 2MiB compressed) packet.");
    }

    int packetLength = out.readableBytes() - 3;
    out.setMedium(0, ProtocolUtils.encode21BitVarInt(packetLength)); // Rewrite packet length
  }

  /**
   * Allocates a new output {@link ByteBuf} for compression, sized based on the estimated result.
   *
   * <p>If the packet is smaller than the threshold, a small heap or direct buffer is allocated.
   * If compression is expected, a larger pre-sized buffer is used based on the expected
   * compression ratio and uncompressed length.</p>
   *
   * @param ctx the Netty channel context
   * @param msg the input packet
   * @param preferDirect whether to prefer direct buffer allocation
   * @return a newly allocated output buffer
   */
  @Override
  protected ByteBuf allocateBuffer(final ChannelHandlerContext ctx, final ByteBuf msg, final boolean preferDirect) {
    int uncompressed = msg.readableBytes();
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
