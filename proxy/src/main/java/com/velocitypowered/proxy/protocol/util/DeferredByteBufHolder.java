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
  public DeferredByteBufHolder(@MonotonicNonNull final ByteBuf backing) {
    this.backing = backing;
  }

  @Override
  public final ByteBuf content() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    if (backing.refCnt() <= 0) {
      throw new IllegalReferenceCountException(backing.refCnt());
    }

    return backing;
  }

  @Override
  public ByteBufHolder copy() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return new DeferredByteBufHolder(backing.copy());
  }

  @Override
  public ByteBufHolder duplicate() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return new DeferredByteBufHolder(backing.duplicate());
  }

  @Override
  public ByteBufHolder retainedDuplicate() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return new DeferredByteBufHolder(backing.retainedDuplicate());
  }

  @Override
  public ByteBufHolder replace(final ByteBuf content) {
    if (content == null) {
      throw new NullPointerException("content");
    }

    this.backing = content;
    return this;
  }

  @Override
  public final int refCnt() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return backing.refCnt();
  }

  @Override
  public ByteBufHolder retain() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    backing.retain();
    return this;
  }

  @Override
  public ByteBufHolder retain(final int increment) {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    backing.retain(increment);
    return this;
  }

  @Override
  public ByteBufHolder touch() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    backing.touch();
    return this;
  }

  @Override
  public ByteBufHolder touch(final Object hint) {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    backing.touch(hint);
    return this;
  }

  @Override
  public final boolean release() {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return backing.release();
  }

  @Override
  public final boolean release(final int decrement) {
    if (backing == null) {
      throw new IllegalStateException("Trying to obtain contents of holder with a null buffer");
    }

    return backing.release(decrement);
  }

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
