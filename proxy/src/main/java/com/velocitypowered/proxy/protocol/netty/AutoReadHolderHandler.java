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

package com.velocitypowered.proxy.protocol.netty;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;

/**
 * A variation on {@link io.netty.handler.flow.FlowControlHandler} that explicitly holds messages on
 * {@code channelRead} and only releases them on an explicit read operation.
 */
public class AutoReadHolderHandler extends ChannelDuplexHandler {

  private final Queue<Object> queuedMessages;

  public AutoReadHolderHandler() {
    this.queuedMessages = new ArrayDeque<>();
  }

  @Override
  public void read(final ChannelHandlerContext ctx) throws Exception {
    drainQueuedMessages(ctx);
    ctx.read();
  }

  private void drainQueuedMessages(final ChannelHandlerContext ctx) {
    if (!this.queuedMessages.isEmpty()) {
      Object queued;
      while ((queued = this.queuedMessages.poll()) != null) {
        ctx.fireChannelRead(queued);
      }

      ctx.fireChannelReadComplete();
    }
  }

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final @NotNull Object msg) {
    if (ctx.channel().config().isAutoRead()) {
      ctx.fireChannelRead(msg);
    } else {
      this.queuedMessages.add(msg);
    }
  }

  @Override
  public void channelReadComplete(final ChannelHandlerContext ctx) {
    if (ctx.channel().config().isAutoRead()) {
      if (!this.queuedMessages.isEmpty()) {
        this.drainQueuedMessages(ctx); // will also call fireChannelReadComplete()
      } else {
        ctx.fireChannelReadComplete();
      }
    }
  }

  @Override
  public void handlerRemoved(final ChannelHandlerContext ctx) {
    for (Object message : this.queuedMessages) {
      ReferenceCountUtil.release(message);
    }

    this.queuedMessages.clear();
  }
}
