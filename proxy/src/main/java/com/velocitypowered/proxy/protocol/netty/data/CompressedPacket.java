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
import static com.velocitypowered.proxy.protocol.util.NettyPreconditions.checkFrame;

import com.velocitypowered.natives.compression.VelocityCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.zip.DataFormatException;

/**
 * Compressed identified packet.
 */
public class CompressedPacket extends IdentifiedPacket {

  /**
   * Default maximum allowed uncompressed packet size (8 MiB).
   */
  private static final int VANILLA_MAXIMUM_UNCOMPRESSED_SIZE = 8 * 1024 * 1024;

  /**
   * Hard upper limit for uncompressed size (128 MiB), used if override is enabled.
   */
  private static final int HARD_MAXIMUM_UNCOMPRESSED_SIZE = 128 * 1024 * 1024;

  /**
   * Maximum uncompressed size permitted during decompression.
   *
   * <p>Can be overridden with {@code -Dvelocity.increased-compression-cap=true} to allow up to 128 MiB.</p>
   */
  private static final int UNCOMPRESSED_CAP =
      Boolean.getBoolean("velocity.increased-compression-cap")
          ? HARD_MAXIMUM_UNCOMPRESSED_SIZE : VANILLA_MAXIMUM_UNCOMPRESSED_SIZE;

  /**
   * Declared uncompressed length in bytes.
   *
   * <p>Used for validating caps and sizing the destination buffer during inflation.</p>
   */
  private final int uncompressedLength;

  /**
   * Compressed payload buffer.
   *
   * <p>This buffer is retained by the creator of this packet. Callers must not release it
   * unless they hold their own reference.</p>
   */
  private final ByteBuf compressedBuf;

  /**
   * Compressor used to inflate the payload.
   *
   * <p>Implementations may be backed by native or pure-Java code.</p>
   */
  private final VelocityCompressor compressor;

  /**
   * Constructs a binary compressed packet.
   *
   * @param packetId Packet ID.
   * @param uncompressedLength Uncompressed packet length.
   * @param compressedBuf Compressed buffer.
   * @param compressor Compressor.
   */
  public CompressedPacket(final int packetId, final int uncompressedLength, final ByteBuf compressedBuf,
                          final VelocityCompressor compressor) {
    super(packetId);
    this.uncompressedLength = uncompressedLength;
    this.compressedBuf = compressedBuf;
    this.compressor = compressor;
  }

  /**
   * Decompresses a buffer.
   *
   * @param allocator Buffer allocator.
   * @return Target buffer.
   * @throws DataFormatException Error occurred during decompression.
   */
  public ByteBuf decompress(final ByteBufAllocator allocator) throws DataFormatException {
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

  /**
   * Returns the declared uncompressed length.
   *
   * @return the uncompressed length in bytes
   */
  public int getUncompressedLength() {
    return this.uncompressedLength;
  }

  /**
   * Returns the compressed payload buffer.
   *
   * <p>The buffer is retained by the creator of this packet. Do not release it unless
   * you have acquired an additional reference.</p>
   *
   * @return the compressed payload buffer
   */
  public ByteBuf getCompressedBuf() {
    return this.compressedBuf;
  }

  /**
   * Returns the compressor used for inflation.
   *
   * @return the compressor instance
   */
  public VelocityCompressor getCompressor() {
    return this.compressor;
  }
}
