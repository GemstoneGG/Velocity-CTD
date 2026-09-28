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

import com.velocitypowered.proxy.connection.MinecraftConnection;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.concurrent.Ticker;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ReadTimeoutHandler} that confirms a due timeout on a later event loop pass, so a loop
 * that was held up drops no connection with data waiting, and leaves out the time reading was
 * paused for backpressure.
 */
public final class VelocityReadTimeoutHandler extends ReadTimeoutHandler {

  private static final long CONFIRMATION_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

  private final long timeoutNanos;
  private Ticker ticker = Ticker.systemTicker();
  private long lastReadNanos;
  private long pausedSinceNanos = -1;
  private long pausedNanos;
  private boolean reading;
  private boolean readSinceCheck;
  private boolean checkOnResume;
  private @Nullable ScheduledFuture<?> pendingCheck;
  private @Nullable ChannelHandlerContext context;
  private @Nullable MinecraftConnection connection;

  /**
   * Creates a handler that closes the connection once it has read nothing for {@code timeout}.
   */
  public VelocityReadTimeoutHandler(long timeout, TimeUnit unit) {
    super(timeout, unit);
    this.timeoutNanos = unit.toNanos(timeout);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    context = ctx;
    ticker = ctx.executor().ticker();
    lastReadNanos = ticker.nanoTime();
    connection = ctx.pipeline().get(MinecraftConnection.class);
    if (connection != null && connection.isPausedForBackpressure()) {
      pausedSinceNanos = lastReadNanos;
    }
    super.handlerAdded(ctx);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    reading = true;
    readSinceCheck = true;
    super.channelRead(ctx, msg);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    if (reading) {
      reading = false;
      lastReadNanos = ticker.nanoTime();
      pausedNanos = 0;
      checkOnResume = false;
      if (pausedSinceNanos >= 0) {
        pausedSinceNanos = lastReadNanos;
      }
    }
    super.channelReadComplete(ctx);
  }

  /**
   * Starts leaving time out of this timeout. Called on the event loop.
   */
  public void backpressurePaused() {
    if (pausedSinceNanos < 0) {
      pausedSinceNanos = ticker.nanoTime();
    }
  }

  /**
   * Stops leaving time out of this timeout, re-checking one that came due meanwhile. Called on the
   * event loop.
   */
  public void backpressureResumed() {
    if (pausedSinceNanos >= 0) {
      pausedNanos += ticker.nanoTime() - pausedSinceNanos;
      pausedSinceNanos = -1;
    }
    if (checkOnResume && pendingCheck == null && context != null) {
      checkOnResume = false;
      checkTimeout(context, false);
    }
  }

  @Override
  protected void readTimedOut(ChannelHandlerContext ctx) {
    if (pendingCheck == null) {
      checkTimeout(ctx, false);
    }
  }

  private void checkTimeout(ChannelHandlerContext ctx, boolean confirming) {
    long now = ticker.nanoTime();
    boolean pausing = pausedSinceNanos >= 0 && isPauseStillNeeded(ctx);
    long paused = pausing ? pausedNanos + now - pausedSinceNanos : pausedNanos;
    long remaining = timeoutNanos - (now - lastReadNanos - paused);
    if (remaining > 0) {
      checkOnResume = pausing;
      if (!pausing) {
        scheduleCheck(ctx, remaining, false);
      }
      return;
    }

    if (!confirming) {
      scheduleCheck(ctx, CONFIRMATION_DELAY_NANOS, true);
      return;
    }

    try {
      super.readTimedOut(ctx);
    } catch (Exception e) {
      ctx.fireExceptionCaught(e);
    }
  }

  private void scheduleCheck(ChannelHandlerContext ctx, long delayNanos, boolean confirming) {
    readSinceCheck = false;
    pendingCheck = ctx.executor().schedule(() -> {
      pendingCheck = null;
      if (!readSinceCheck) {
        checkTimeout(ctx, confirming);
      }
    }, delayNanos, TimeUnit.NANOSECONDS);
  }

  private boolean isPauseStillNeeded(ChannelHandlerContext ctx) {
    if (connection == null) {
      connection = ctx.pipeline().get(MinecraftConnection.class);
    }
    return connection != null && connection.isBackpressureBounded();
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    cancelCheck();
    context = null;
    super.handlerRemoved(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    cancelCheck();
    super.channelInactive(ctx);
  }

  private void cancelCheck() {
    if (pendingCheck != null) {
      pendingCheck.cancel(false);
      pendingCheck = null;
    }
  }
}
