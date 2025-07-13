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

package com.velocitypowered.natives.compression;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.velocitypowered.natives.compression.CompressorUtils.ZLIB_BUFFER_SIZE;

import com.velocitypowered.natives.util.BufferPreference;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Implements deflate compression by wrapping {@link Deflater} and {@link Inflater}.
 */
public final class JavaVelocityCompressor implements VelocityCompressor {

  /**
   * A {@link VelocityCompressorFactory} for creating instances of {@link JavaVelocityCompressor}.
   *
   * <p>This factory allows the {@link JavaVelocityCompressor} to be registered or used
   * where a generic {@link VelocityCompressor} implementation is required.</p>
   */
  public static final VelocityCompressorFactory FACTORY = JavaVelocityCompressor::new;

  /**
   * The underlying {@link Deflater} used to compress data using the DEFLATE algorithm.
   */
  private final Deflater deflater;

  /**
   * The underlying {@link Inflater} used to decompress DEFLATE-compressed data.
   */
  private final Inflater inflater;

  /**
   * Indicates whether this compressor instance has been disposed.
   */
  private boolean disposed = false;

  private JavaVelocityCompressor(final int level) {
    this.deflater = new Deflater(level);
    this.inflater = new Inflater();
  }

  @Override
  public void inflate(final ByteBuf source, final ByteBuf destination, final int uncompressedSize)
      throws DataFormatException {
    ensureNotDisposed();

    // We (probably) can't nicely deal with >=1 buffer nicely, so let's scream loudly.
    checkArgument(source.nioBufferCount() == 1, "source has multiple backing buffers");
    checkArgument(destination.nioBufferCount() == 1, "destination has multiple backing buffers");

    final int origIdx = source.readerIndex();
    inflater.setInput(source.nioBuffer());

    try {
      final int readable = source.readableBytes();
      while (!inflater.finished() && inflater.getBytesRead() < readable) {
        if (!destination.isWritable()) {
          destination.ensureWritable(ZLIB_BUFFER_SIZE);
        }

        ByteBuffer destNioBuf = destination.nioBuffer(destination.writerIndex(),
            destination.writableBytes());
        int produced = inflater.inflate(destNioBuf);
        destination.writerIndex(destination.writerIndex() + produced);
      }

      if (!inflater.finished()) {
        throw new DataFormatException("Received a deflate stream that was too large, wanted "
            + uncompressedSize);
      }
      source.readerIndex(origIdx + inflater.getTotalIn());
    } finally {
      inflater.reset();
    }
  }

  @Override
  public void deflate(final ByteBuf source, final ByteBuf destination) {
    ensureNotDisposed();

    // We (probably) can't nicely deal with >=1 buffer nicely, so let's scream loudly.
    checkArgument(source.nioBufferCount() == 1, "source has multiple backing buffers");
    checkArgument(destination.nioBufferCount() == 1, "destination has multiple backing buffers");

    final int origIdx = source.readerIndex();
    deflater.setInput(source.nioBuffer());
    deflater.finish();

    while (!deflater.finished()) {
      if (!destination.isWritable()) {
        destination.ensureWritable(ZLIB_BUFFER_SIZE);
      }

      ByteBuffer destNioBuf = destination.nioBuffer(destination.writerIndex(),
          destination.writableBytes());
      int produced = deflater.deflate(destNioBuf);
      destination.writerIndex(destination.writerIndex() + produced);
    }

    source.readerIndex(origIdx + deflater.getTotalIn());
    deflater.reset();
  }

  @Override
  public void close() {
    disposed = true;
    deflater.end();
    inflater.end();
  }

  private void ensureNotDisposed() {
    checkState(!disposed, "Object already disposed");
  }

  @Override
  public BufferPreference preferredBufferType() {
    return BufferPreference.DIRECT_PREFERRED;
  }
}
