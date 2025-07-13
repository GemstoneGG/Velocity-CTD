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

  @Override
  public final void readFully(final byte @NotNull [] b) {
    in.readBytes(b);
  }

  @Override
  public final void readFully(final byte @NotNull [] b, final int off, final int len) {
    in.readBytes(b, off, len);
  }

  @Override
  public final int skipBytes(final int n) {
    in.skipBytes(n);
    return n;
  }

  @Override
  public final boolean readBoolean() {
    return in.readBoolean();
  }

  @Override
  public final byte readByte() {
    return in.readByte();
  }

  @Override
  public final int readUnsignedByte() {
    return in.readUnsignedByte() & 0xFF;
  }

  @Override
  public final short readShort() {
    return in.readShort();
  }

  @Override
  public final int readUnsignedShort() {
    return in.readUnsignedShort();
  }

  @Override
  public final char readChar() {
    return in.readChar();
  }

  @Override
  public final int readInt() {
    return in.readInt();
  }

  @Override
  public final long readLong() {
    return in.readLong();
  }

  @Override
  public final float readFloat() {
    return in.readFloat();
  }

  @Override
  public final double readDouble() {
    return in.readDouble();
  }

  @Override
  public final String readLine() {
    throw new UnsupportedOperationException();
  }

  @Override
  public final @NotNull String readUTF() {
    try {
      return DataInputStream.readUTF(this);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
