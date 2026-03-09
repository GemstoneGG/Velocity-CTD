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

package com.velocitypowered.proxy.protocol.util;

import com.google.common.io.ByteArrayDataOutput;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link ByteArrayDataOutput} equivalent to {@link ByteBufDataInput}.
 */
public class ByteBufDataOutput extends OutputStream implements ByteArrayDataOutput {

  /**
   * The backing {@link ByteBuf} to which data is written.
   */
  private final ByteBuf buf;

  /**
   * A wrapper {@link DataOutputStream} used for writing UTF-8 encoded strings.
   */
  private final DataOutputStream utf8out;

  /**
   * Constructs a new {@code ByteBufDataOutput} instance using the provided {@link ByteBuf}.
   *
   * @param buf the Netty buffer to write to
   */
  public ByteBufDataOutput(final ByteBuf buf) {
    this.buf = buf;
    this.utf8out = new DataOutputStream(this);
  }

  /**
   * Returns a copy of the data currently written to the underlying buffer.
   *
   * @return a byte array containing the buffer's contents
   */
  @Override
  public byte @NotNull [] toByteArray() {
    return ByteBufUtil.getBytes(buf);
  }

  /**
   * Writes a single byte to the output buffer.
   *
   * @param b the byte value to write (only the least-significant 8 bits are used)
   */
  @Override
  public void write(final int b) {
    buf.writeByte(b);
  }

  /**
   * Writes the entire byte array to the output buffer.
   *
   * @param b the byte array to write
   */
  @Override
  public void write(final byte @NotNull [] b) {
    buf.writeBytes(b);
  }

  /**
   * Writes {@code len} bytes from the given byte array starting at {@code off}.
   *
   * @param b   the byte array to write from
   * @param off the starting offset in the array
   * @param len the number of bytes to write
   */
  @Override
  public void write(final byte @NotNull [] b, final int off, final int len) {
    buf.writeBytes(b, off, len);
  }

  /**
   * Writes a boolean value to the buffer.
   *
   * @param v the boolean value to write
   */
  @Override
  public void writeBoolean(final boolean v) {
    buf.writeBoolean(v);
  }

  /**
   * Writes a single byte value to the buffer.
   *
   * @param v the byte value
   */
  @Override
  public void writeByte(final int v) {
    buf.writeByte(v);
  }

  /**
   * Writes a 2-byte short value to the buffer.
   *
   * @param v the short value
   */
  @Override
  public void writeShort(final int v) {
    buf.writeShort(v);
  }

  /**
   * Writes a 2-byte character to the buffer.
   *
   * @param v the char value
   */
  @Override
  public void writeChar(final int v) {
    buf.writeChar(v);
  }

  /**
   * Writes a 4-byte integer value to the buffer.
   *
   * @param v the int value
   */
  @Override
  public void writeInt(final int v) {
    buf.writeInt(v);
  }

  /**
   * Writes an 8-byte long value to the buffer.
   *
   * @param v the long value
   */
  @Override
  public void writeLong(final long v) {
    buf.writeLong(v);
  }

  /**
   * Writes a 4-byte float value to the buffer.
   *
   * @param v the float value
   */
  @Override
  public void writeFloat(final float v) {
    buf.writeFloat(v);
  }

  /**
   * Writes an 8-byte double value to the buffer.
   *
   * @param v the double value
   */
  @Override
  public void writeDouble(final double v) {
    buf.writeDouble(v);
  }

  /**
   * Writes the characters of the string as ASCII bytes.
   *
   * @param s the string to write
   */
  @Override
  public void writeBytes(final @NotNull String s) {
    buf.writeCharSequence(s, StandardCharsets.US_ASCII);
  }

  /**
   * Writes each character of the string as a 2-byte {@code char}.
   *
   * @param s the string to write
   */
  @Override
  public void writeChars(final String s) {
    for (char c : s.toCharArray()) {
      buf.writeChar(c);
    }
  }

  /**
   * Writes the string in UTF-8 format using {@link DataOutputStream#writeUTF(String)}.
   *
   * @param s the string to write
   * @throws IllegalStateException if an I/O error occurs
   */
  @Override
  public void writeUTF(final @NotNull String s) {
    try {
      this.utf8out.writeUTF(s);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close() {
  }
}
