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

package com.velocitypowered.proxy.protocol.netty.data;

import static com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible;
import static com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer;

import com.velocitypowered.natives.compression.VelocityCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.zip.DataFormatException;

/**
 * An {@link IdentifiedPacket} carrying a fully-decompressed payload. The {@code packetBuf}'s
 * reader index points at the start of the packet body (the packet id has already been read).
 * Ownership of {@code packetBuf} transfers to whichever pipeline stage consumes the packet.
 */
public class UncompressedPacket extends IdentifiedPacket {

  private final ByteBuf packetBuf;

  public UncompressedPacket(int packetId, ByteBuf packetBuf) {
    super(packetId);
    this.packetBuf = packetBuf;
  }

  public ByteBuf getPacketBuf() {
    return this.packetBuf;
  }

  /**
   * Compresses {@link #getPacketBuf()} into a freshly allocated buffer using the given
   * compressor. Used by the compression-encoder stage when an uncompressed payload crosses the
   * compression threshold and has to be compressed before going on the wire.
   *
   * @param compressor the compressor to use
   * @param allocator  the allocator to draw the destination buffer from
   * @return the compressed buffer, owned by the caller
   * @throws DataFormatException if the compressor fails
   */
  public ByteBuf compress(VelocityCompressor compressor, ByteBufAllocator allocator)
      throws DataFormatException {
    ByteBuf compressed = preferredBuffer(allocator, compressor, 256);
    try {
      return compress(compressor, allocator, compressed);
    } catch (DataFormatException e) {
      compressed.release();
      throw e;
    }
  }

  /**
   * Compresses {@link #getPacketBuf()} into a caller-provided destination buffer.
   *
   * @param compressor the compressor to use
   * @param allocator  the allocator used to materialize a compressor-compatible source buffer
   * @param compressed the destination buffer; ownership remains with the caller
   * @return {@code compressed}, for fluent chaining
   * @throws DataFormatException if the compressor fails, or if the compressed length exceeds 2MiB
   */
  public ByteBuf compress(VelocityCompressor compressor, ByteBufAllocator allocator,
                          ByteBuf compressed) throws DataFormatException {
    ByteBuf compatibleIn = ensureCompatible(allocator, compressor, this.packetBuf.duplicate());

    int originalWriterIndex = compressed.writerIndex();
    try {
      compressor.deflate(compatibleIn, compressed);
    } finally {
      compatibleIn.release();
    }

    int compressedLength = compressed.writerIndex() - originalWriterIndex;
    if (compressedLength >= 1 << 21) {
      throw new DataFormatException("Compressed packet is very large (over 2MiB).");
    }

    return compressed;
  }
}
