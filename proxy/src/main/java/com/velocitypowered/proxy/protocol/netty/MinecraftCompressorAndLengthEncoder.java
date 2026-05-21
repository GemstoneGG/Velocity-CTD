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
 * Compresses and length-frames outbound packets, with a four-way switch on input type and
 * size relative to the configured compression threshold.
 */
public class MinecraftCompressorAndLengthEncoder extends MessageToByteEncoder<IdentifiedPacket> {

  private int threshold;

  private final VelocityCompressor compressor;

  public MinecraftCompressorAndLengthEncoder(int threshold, VelocityCompressor compressor) {
    this.threshold = threshold;
    this.compressor = compressor;
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, IdentifiedPacket msg, ByteBuf out)
      throws Exception {
    if (msg instanceof UncompressedPacket up) {
      encodeUncompressed(ctx, up, out);
    } else if (msg instanceof CompressedPacket cp) {
      encodeCompressed(ctx, cp, out);
    } else {
      throw new IllegalStateException("Unsupported IdentifiedPacket subtype: "
          + msg.getClass().getName());
    }
  }

  private void encodeUncompressed(ChannelHandlerContext ctx, UncompressedPacket msg, ByteBuf out)
      throws DataFormatException {
    ByteBuf payload = msg.getPacketBuf();
    int uncompressed = payload.readableBytes();
    try {
      if (threshold <= 0 || uncompressed < threshold) {
        // Under the compression threshold: emit with a 0 uncompressed-size sentinel.
        ProtocolUtils.writeVarInt(out, uncompressed + 1);
        ProtocolUtils.writeVarInt(out, 0);
        out.writeBytes(payload);
      } else {
        deflateAndFrame(ctx, payload, out);
      }
    } finally {
      payload.release();
    }
  }

  private void encodeCompressed(ChannelHandlerContext ctx, CompressedPacket msg, ByteBuf out)
      throws DataFormatException {
    ByteBuf compressedPayload = msg.getCompressedBuf();
    int uncompressedLength = msg.getUncompressedLength();
    try {
      if (threshold <= 0 || uncompressedLength < threshold) {
        ProtocolUtils.writeVarInt(out, uncompressedLength + 1);
        ProtocolUtils.writeVarInt(out, 0);
        ByteBuf decompressed = msg.decompress(ctx.alloc());
        try {
          out.writeBytes(decompressed);
        } finally {
          decompressed.release();
        }
      } else {
        int compressedLength = compressedPayload.readableBytes();
        int uncompressedLengthVarIntBytes = ProtocolUtils.varIntBytes(uncompressedLength);
        ProtocolUtils.writeVarInt(out, compressedLength + uncompressedLengthVarIntBytes);
        ProtocolUtils.writeVarInt(out, uncompressedLength);
        out.writeBytes(compressedPayload);
      }
    } finally {
      compressedPayload.release();
    }
  }

  private void deflateAndFrame(ChannelHandlerContext ctx, ByteBuf payload, ByteBuf out)
      throws DataFormatException {
    int uncompressed = payload.readableBytes();

    out.writeMedium(0); // Reserve the packet-length 3-byte varint slot.
    ProtocolUtils.writeVarInt(out, uncompressed);
    ByteBuf compatibleIn = MoreByteBufUtils.ensureCompatible(ctx.alloc(), compressor, payload);

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
    out.setMedium(0, ProtocolUtils.encode21BitVarInt(packetLength));
  }

  @Override
  protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, IdentifiedPacket msg,
                                   boolean preferDirect) throws Exception {
    if (msg instanceof UncompressedPacket up) {
      int uncompressed = up.getPacketBuf().readableBytes();
      if (threshold <= 0 || uncompressed < threshold) {
        int finalBufferSize = uncompressed + 1;
        finalBufferSize += ProtocolUtils.varIntBytes(finalBufferSize);
        return IS_JAVA_CIPHER
            ? ctx.alloc().heapBuffer(finalBufferSize)
            : ctx.alloc().directBuffer(finalBufferSize);
      }

      // Compression path: reserve room for worst-case framing + an estimate of compressed size.
      int initialBufferSize = (uncompressed - 1) + 3 + ProtocolUtils.varIntBytes(uncompressed);
      return MoreByteBufUtils.preferredBuffer(ctx.alloc(), compressor, initialBufferSize);
    }

    if (msg instanceof CompressedPacket cp) {
      int compressedLength = cp.getCompressedBuf().readableBytes();
      int uncompressedLength = cp.getUncompressedLength();
      if (threshold <= 0 || uncompressedLength < threshold) {
        int finalBufferSize = uncompressedLength + 1;
        finalBufferSize += ProtocolUtils.varIntBytes(finalBufferSize);
        return IS_JAVA_CIPHER
            ? ctx.alloc().heapBuffer(finalBufferSize)
            : ctx.alloc().directBuffer(finalBufferSize);
      }

      int finalBufferSize = compressedLength
          + ProtocolUtils.varIntBytes(compressedLength + ProtocolUtils.varIntBytes(uncompressedLength))
          + ProtocolUtils.varIntBytes(uncompressedLength);
      return IS_JAVA_CIPHER
          ? ctx.alloc().heapBuffer(finalBufferSize)
          : ctx.alloc().directBuffer(finalBufferSize);
    }

    return super.allocateBuffer(ctx, msg, preferDirect);
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) {
    compressor.close();
  }

  public void setThreshold(int threshold) {
    this.threshold = threshold;
  }
}
