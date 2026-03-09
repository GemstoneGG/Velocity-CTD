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

package com.velocitypowered.natives.compression;

import com.velocitypowered.natives.Disposable;
import com.velocitypowered.natives.Native;
import io.netty.buffer.ByteBuf;
import java.util.zip.DataFormatException;

/**
 * Provides an interface to inflate and deflate {@link ByteBuf}s using zlib or a compatible
 * implementation.
 */
public interface VelocityCompressor extends Disposable, Native {

  /**
   * Decompresses the provided compressed {@code source} buffer into the {@code destination} buffer.
   *
   * @param source the compressed input buffer
   * @param destination the buffer to write decompressed data into
   * @param uncompressedSize the expected size of the decompressed data
   * @throws DataFormatException if decompression fails due to corrupted or invalid data
   */
  void inflate(ByteBuf source, ByteBuf destination, int uncompressedSize) throws DataFormatException;

  /**
   * Compresses the data from the {@code source} buffer and writes the compressed result into
   * the {@code destination} buffer.
   *
   * @param source the raw input buffer to compress
   * @param destination the buffer to write compressed data into
   * @throws DataFormatException if compression fails due to an internal error
   */
  void deflate(ByteBuf source, ByteBuf destination) throws DataFormatException;
}
