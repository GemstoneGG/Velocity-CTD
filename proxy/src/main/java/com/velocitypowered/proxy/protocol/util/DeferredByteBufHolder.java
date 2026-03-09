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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.IllegalReferenceCountException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A special-purpose implementation of {@code ByteBufHolder} that can defer accepting its buffer.
 * This is required because Velocity packets are, for better or worse, mutable.
 */
public class DeferredByteBufHolder implements ByteBufHolder {

  /**
   * The backing {@link ByteBuf} that holds the data for this holder.
   *
   * <p>This buffer may be {@code null} until it is explicitly initialized
   * using {@link #replace(ByteBuf)}.</p>
   */
  @MonotonicNonNull private ByteBuf backing;

  /**
   * Constructs a new {@code DeferredByteBufHolder} with an optional initial buffer.
   *
   * @param backing the initial {@link ByteBuf}, or {@code null} if not yet assigned
   */
  public DeferredByteBufHolder(final @MonotonicNonNull ByteBuf backing) {
    this.backing = backing;
  }

  /**
   * Returns the backing {@link ByteBuf} for this holder.
   *
   * @return the content buffer
   * @throws IllegalStateException if the backing buffer is {@code null}
   * @throws IllegalReferenceCountException if the buffer has been released
   */
  @Override
  public ByteBuf content() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    if (backing.refCnt() <= 0) {
      throw new IllegalReferenceCountException(backing.refCnt());
    }

    return backing;
  }

  /**
   * Creates a deep copy of this buffer holder.
   *
   * @return a new {@code DeferredByteBufHolder} with a copied buffer
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public ByteBufHolder copy() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return new DeferredByteBufHolder(backing.copy());
  }

  /**
   * Creates a shallow duplicate of this buffer holder.
   *
   * @return a new {@code DeferredByteBufHolder} with a duplicated buffer
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public ByteBufHolder duplicate() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return new DeferredByteBufHolder(backing.duplicate());
  }

  /**
   * Creates a retained duplicate of this buffer holder.
   *
   * @return a new {@code DeferredByteBufHolder} with a retained duplicate
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public ByteBufHolder retainedDuplicate() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return new DeferredByteBufHolder(backing.retainedDuplicate());
  }

  /**
   * Replaces the internal buffer with a new one.
   *
   * @param content the new {@link ByteBuf} to use
   * @return this holder instance
   * @throws NullPointerException if {@code content} is {@code null}
   */
  @Override
  public ByteBufHolder replace(final ByteBuf content) {
    if (content == null) {
      throw new NullPointerException("content");
    }

    this.backing = content;
    return this;
  }

  /**
   * Returns the reference count of the backing buffer.
   *
   * @return the reference count
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public int refCnt() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return backing.refCnt();
  }

  /**
   * Increments the reference count of the backing buffer by 1.
   *
   * @return this holder
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public ByteBufHolder retain() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    backing.retain();
    return this;
  }

  /**
   * Increments the reference count of the backing buffer.
   *
   * @param increment the amount to increment
   * @return this holder
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public ByteBufHolder retain(final int increment) {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    backing.retain(increment);
    return this;
  }

  /**
   * Records access to the buffer for leak detection.
   *
   * @return this holder
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public ByteBufHolder touch() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    backing.touch();
    return this;
  }

  /**
   * Records access to the buffer for leak detection with a hint.
   *
   * @param hint an arbitrary object to record with the access
   * @return this holder
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public ByteBufHolder touch(final Object hint) {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    backing.touch(hint);
    return this;
  }

  /**
   * Decrements the reference count and releases the buffer if it reaches zero.
   *
   * @return {@code true} if the buffer was released
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public boolean release() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return backing.release();
  }

  /**
   * Decrements the reference count by the specified value and releases the buffer if zero.
   *
   * @param decrement the amount to decrease the reference count
   * @return {@code true} if the buffer was released
   * @throws IllegalStateException if the backing buffer is {@code null}
   */
  @Override
  public boolean release(final int decrement) {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return backing.release(decrement);
  }

  /**
   * Returns a string representation of this buffer holder, including the buffer state.
   *
   * @return string representation of this holder
   */
  @Override
  public String toString() {
    String str = "DeferredByteBufHolder[";
    if (backing == null) {
      str += "null";
    } else {
      str += backing.toString();
    }

    return str + "]";
  }
}
