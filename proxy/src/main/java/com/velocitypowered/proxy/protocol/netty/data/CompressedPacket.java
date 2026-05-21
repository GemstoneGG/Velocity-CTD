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
import static com.velocitypowered.proxy.protocol.util.NettyPreconditions.checkFrame;

import com.velocitypowered.natives.compression.VelocityCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.zip.DataFormatException;

/**
 * An {@link IdentifiedPacket} carrying a still-compressed payload alongside the claimed
 * uncompressed length and a reference to the compressor that produced it.
 */
public class CompressedPacket extends IdentifiedPacket {

  private static final int VANILLA_MAXIMUM_UNCOMPRESSED_SIZE = 8 * 1024 * 1024; // 8MiB
  private static final int HARD_MAXIMUM_UNCOMPRESSED_SIZE = 128 * 1024 * 1024; // 128MiB

  private static final int UNCOMPRESSED_CAP =
      Boolean.getBoolean("velocity.increased-compression-cap")
          ? HARD_MAXIMUM_UNCOMPRESSED_SIZE : VANILLA_MAXIMUM_UNCOMPRESSED_SIZE;

  private final int uncompressedLength;
  private final ByteBuf compressedBuf;
  private final VelocityCompressor compressor;

  /**
   * Constructs a compressed packet from the still-compressed bytes left over after the
   * compression decoder extracted the packet id via partial inflation.
   *
   * @param packetId           the packet id, extracted by the decoder stage
   * @param uncompressedLength the claimed uncompressed length from the wire framing
   * @param compressedBuf      the compressed payload remaining after the packet-id prefix
   * @param compressor         the compressor whose dictionary state matches {@code compressedBuf}
   */
  public CompressedPacket(int packetId, int uncompressedLength, ByteBuf compressedBuf,
                          VelocityCompressor compressor) {
    super(packetId);
    this.uncompressedLength = uncompressedLength;
    this.compressedBuf = compressedBuf;
    this.compressor = compressor;
  }

  /**
   * Decompresses the carried payload into a freshly allocated buffer using the original
   * compressor. Called by the decoder when the proxy turns out to have a typed listener for
   * the packet id after all, and by the encoder when the encoder's compression threshold has
   * been lowered below this packet's claimed uncompressed size.
   *
   * @param allocator the allocator to draw the destination buffer from
   * @return the decompressed buffer, owned by the caller
   * @throws DataFormatException if decompression fails or the claimed length exceeds the cap
   */
  public ByteBuf decompress(ByteBufAllocator allocator) throws DataFormatException {
    checkFrame(this.uncompressedLength <= UNCOMPRESSED_CAP,
        "Uncompressed size %s exceeds hard threshold of %s", this.uncompressedLength,
        UNCOMPRESSED_CAP);

    ByteBuf compatibleIn = ensureCompatible(allocator, compressor, this.compressedBuf.duplicate());
    ByteBuf uncompressed = preferredBuffer(allocator, compressor, this.uncompressedLength);
    try {
      compressor.inflate(compatibleIn, uncompressed, this.uncompressedLength);
      return uncompressed;
    } catch (Exception e) {
      uncompressed.release();
      throw e;
    } finally {
      compatibleIn.release();
    }
  }

  public int getUncompressedLength() {
    return this.uncompressedLength;
  }

  public ByteBuf getCompressedBuf() {
    return this.compressedBuf;
  }

  public VelocityCompressor getCompressor() {
    return this.compressor;
  }
}
