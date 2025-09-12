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

import static com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible;
import static com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer;

import com.velocitypowered.natives.compression.VelocityCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.zip.DataFormatException;

/**
 * Identified packet with an uncompressed payload.
 *
 * <p>The payload is stored as a {@link ByteBuf}. Use {@link #compress(VelocityCompressor, ByteBufAllocator)}
 * (or the overload accepting a destination buffer) to obtain a compressed form.</p>
 */
public class UncompressedPacket extends IdentifiedPacket {

  /**
   * Uncompressed payload buffer.
   *
   * <p>This buffer is retained/owned by the creator of this packet. Callers should
   * {@link ByteBuf#retain() retain} if they need to hold on to it and must not
   * {@link ByteBuf#release() release} it unless they own an additional reference.</p>
   */
  private final ByteBuf packetBuf;

  /**
   * Creates an uncompressed identified packet.
   *
   * @param packetId the protocol packet identifier
   * @param packetBuf the uncompressed payload buffer (retained by the creator)
   */
  public UncompressedPacket(final int packetId, final ByteBuf packetBuf) {
    super(packetId);
    this.packetBuf = packetBuf;
  }

  /**
   * Allocates a new destination buffer and compresses {@link #packetBuf} into it.
   *
   * <p>The returned buffer is owned by the caller and must be released. The initial
   * capacity is sized conservatively; Netty may expand it as needed during writes.</p>
   *
   * @param compressor the compressor to use
   * @param allocator the allocator used to create the destination buffer
   * @return a buffer containing the compressed payload
   * @throws DataFormatException if compression fails
   */
  public ByteBuf compress(final VelocityCompressor compressor, final ByteBufAllocator allocator)
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
   * Compresses {@link #packetBuf} into the provided destination buffer.
   *
   * <p>This method writes the compressed bytes to {@code compressed} starting at its current
   * writer index and advances that index accordingly. The reader index of {@code compressed}
   * is not modified. To guard against pathological expansion, a hard limit is enforced:
   * if more than {@code 1 << 21} bytes (≈ 2&nbsp;MiB, the maximum 21-bit varint length field)
   * would be written, a {@link DataFormatException} is thrown.</p>
   *
   * <p>The caller retains ownership of {@code compressed} and is responsible for releasing it.</p>
   *
   * @param compressor the compressor to use
   * @param allocator the allocator used for any temporary compatible views
   * @param compressed the destination buffer to receive compressed data
   * @return the same {@code compressed} buffer, with its writer index advanced
   * @throws DataFormatException if compression fails or the result would exceed the safety limit
   */
  public ByteBuf compress(final VelocityCompressor compressor, final ByteBufAllocator allocator,
                          final ByteBuf compressed) throws DataFormatException {
    ByteBuf compatibleIn = ensureCompatible(allocator, compressor, this.packetBuf.duplicate());

    int originalWriterIndex = compressed.writerIndex();
    try {
      compressor.deflate(compatibleIn, compressed);
    } finally {
      compatibleIn.release();
    }

    int compressedLength = compressed.writerIndex() - originalWriterIndex;
    if (compressedLength >= 1 << 21) {
      throw new DataFormatException("Compressed packet is very large (over 2 MiB).");
    }

    return compressed;
  }

  /**
   * Returns the uncompressed payload buffer.
   *
   * <p>If you need to retain it beyond the current pipeline operation, call
   * {@link ByteBuf#retain()} to acquire an additional reference.</p>
   *
   * @return the uncompressed payload buffer
   */
  public ByteBuf getPacketBuf() {
    return this.packetBuf;
  }
}
