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

import com.google.common.io.ByteArrayDataInput;
import io.netty.buffer.ByteBuf;
import java.io.DataInputStream;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

/**
 * A wrapper around {@link io.netty.buffer.ByteBuf} that implements the exception-free
 * {@link ByteArrayDataInput} interface from Guava.
 */
public class ByteBufDataInput implements ByteArrayDataInput {

  /**
   * The underlying {@link ByteBuf} being read from.
   */
  private final ByteBuf in;

  /**
   * Creates a new ByteBufDataInput instance. The ByteBufDataInput simply "borrows" the ByteBuf
   * while it is in use.
   *
   * @param buf the buffer to read from
   */
  public ByteBufDataInput(final ByteBuf buf) {
    this.in = buf;
  }

  /**
   * Returns the underlying {@link ByteBuf} that this wrapper reads from.
   *
   * @return the wrapped buffer
   */
  public ByteBuf unwrap() {
    return in;
  }

  /**
   * Reads bytes from this input stream into the provided array.
   *
   * @param b the byte array to read into
   * @throws IndexOutOfBoundsException if not enough readable bytes are available
   */
  @Override
  public void readFully(final byte @NotNull [] b) {
    in.readBytes(b);
  }

  /**
   * Reads {@code len} bytes into the given array starting at {@code off}.
   *
   * @param b the byte array to read into
   * @param off the starting offset in the array
   * @param len the number of bytes to read
   * @throws IndexOutOfBoundsException if not enough readable bytes are available
   */
  @Override
  public void readFully(final byte @NotNull [] b, final int off, final int len) {
    in.readBytes(b, off, len);
  }

  /**
   * Skips over and discards {@code n} bytes of data from this input stream.
   *
   * @param n the number of bytes to skip
   * @return the actual number of bytes skipped (always {@code n})
   */
  @Override
  public int skipBytes(final int n) {
    in.skipBytes(n);
    return n;
  }

  /**
   * Reads one input byte and returns {@code true} if the byte is nonzero.
   *
   * @return {@code true} if the byte read is nonzero, {@code false} otherwise
   */
  @Override
  public boolean readBoolean() {
    return in.readBoolean();
  }

  /**
   * Reads and returns one signed byte from the input.
   *
   * @return the 8-bit {@code byte} value read
   */
  @Override
  public byte readByte() {
    return in.readByte();
  }

  /**
   * Reads one unsigned byte and returns it as an {@code int} in the range {@code 0} through {@code 255}.
   *
   * @return the unsigned byte value
   */
  @Override
  public int readUnsignedByte() {
    return in.readUnsignedByte() & 0xFF;
  }

  /**
   * Reads a signed 16-bit value from the input.
   *
   * @return the {@code short} value read
   */
  @Override
  public short readShort() {
    return in.readShort();
  }

  /**
   * Reads an unsigned 16-bit value and returns it as an {@code int}.
   *
   * @return the unsigned short value
   */
  @Override
  public int readUnsignedShort() {
    return in.readUnsignedShort();
  }

  /**
   * Reads two bytes and returns a Unicode character.
   *
   * @return the {@code char} read
   */
  @Override
  public char readChar() {
    return in.readChar();
  }

  /**
   * Reads four bytes and returns an {@code int} value.
   *
   * @return the {@code int} read
   */
  @Override
  public int readInt() {
    return in.readInt();
  }

  /**
   * Reads eight bytes and returns a {@code long} value.
   *
   * @return the {@code long} read
   */
  @Override
  public long readLong() {
    return in.readLong();
  }

  /**
   * Reads four bytes and returns a {@code float} value.
   *
   * @return the {@code float} read
   */
  @Override
  public float readFloat() {
    return in.readFloat();
  }

  /**
   * Reads eight bytes and returns a {@code double} value.
   *
   * @return the {@code double} read
   */
  @Override
  public double readDouble() {
    return in.readDouble();
  }

  /**
   * Unsupported operation; {@code readLine()} is not implemented.
   *
   * @return never returns normally
   * @throws UnsupportedOperationException always thrown
   */
  @Override
  public String readLine() {
    throw new UnsupportedOperationException();
  }

  /**
   * Reads a UTF-8 encoded string using {@link DataInputStream#readUTF}.
   *
   * @return the decoded string
   * @throws IllegalStateException if an {@link IOException} occurs during reading
   */
  @Override
  public @NotNull String readUTF() {
    try {
      return DataInputStream.readUTF(this);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
