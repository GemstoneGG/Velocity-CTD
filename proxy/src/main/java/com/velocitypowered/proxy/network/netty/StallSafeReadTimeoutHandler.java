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

package com.velocitypowered.proxy.network.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ReadTimeoutHandler} that does not mistake a stalled event loop for a silent peer.
 *
 * <p>Each pass of a Netty event loop polls its channels and then runs the scheduled tasks that
 * have come due. After the loop was blocked (a handler or task doing blocking work, or a long
 * pause of the whole process), the idle check runs before the data that arrived meanwhile has
 * been read, so every connection on that loop looks idle at once and is dropped. This handler
 * treats Netty's timeout as a suspicion and confirms it on a later pass, which only runs after the
 * loop has polled its channels again: a connection that read anything in between stays open, and
 * one that really sent nothing times out about a millisecond later than it otherwise would.
 */
public final class StallSafeReadTimeoutHandler extends ReadTimeoutHandler {

  private static final long CONFIRMATION_DELAY_MILLIS = 1;

  private boolean readSinceSuspicion;
  private @Nullable ScheduledFuture<?> confirmation;

  /**
   * Creates a handler that closes the connection once it has read nothing for {@code timeout}.
   *
   * @param timeout the read timeout
   * @param unit the unit of {@code timeout}
   */
  public StallSafeReadTimeoutHandler(long timeout, TimeUnit unit) {
    super(timeout, unit);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    readSinceSuspicion = true;
    super.channelRead(ctx, msg);
  }

  @Override
  protected void readTimedOut(ChannelHandlerContext ctx) {
    if (confirmation != null) {
      return;
    }

    readSinceSuspicion = false;
    confirmation = ctx.executor().schedule(
        () -> confirmTimeout(ctx), CONFIRMATION_DELAY_MILLIS, TimeUnit.MILLISECONDS);
  }

  private void confirmTimeout(ChannelHandlerContext ctx) {
    confirmation = null;
    if (readSinceSuspicion) {
      return;
    }

    try {
      super.readTimedOut(ctx);
    } catch (Exception e) {
      ctx.fireExceptionCaught(e);
    }
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    cancelConfirmation();
    super.handlerRemoved(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    cancelConfirmation();
    super.channelInactive(ctx);
  }

  private void cancelConfirmation() {
    if (confirmation != null) {
      confirmation.cancel(false);
      confirmation = null;
    }
  }
}
