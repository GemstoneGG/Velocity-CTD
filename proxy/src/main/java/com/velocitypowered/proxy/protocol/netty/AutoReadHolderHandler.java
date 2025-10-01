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

  /**
   * Queue of messages that have been received via {@code channelRead} but not yet propagated
   * because {@code autoRead} is disabled.
   */
  private final Queue<Object> queuedMessages;

  /**
   * Constructs a new {@code AutoReadHolderHandler}.
   */
  public AutoReadHolderHandler() {
    this.queuedMessages = new ArrayDeque<>();
  }

  /**
   * Processes all queued messages before performing a downstream read operation.
   *
   * <p>This ensures that previously held messages are propagated before new reads are issued.</p>
   *
   * @param ctx the Netty channel context
   * @throws Exception if an error occurs during read propagation
   */
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

  /**
   * Either immediately forwards or queues the incoming message depending on {@code autoRead} status.
   *
   * @param ctx the Netty channel context
   * @param msg the received message
   */
  @Override
  public void channelRead(final ChannelHandlerContext ctx, final @NotNull Object msg) {
    if (ctx.channel().config().isAutoRead()) {
      ctx.fireChannelRead(msg);
    } else {
      this.queuedMessages.add(msg);
    }
  }

  /**
   * Propagates a {@code channelReadComplete} if {@code autoRead} is enabled,
   * or drains any remaining queued messages first.
   *
   * @param ctx the Netty channel context
   */
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

  /**
   * Releases any queued messages when the handler is removed from the pipeline.
   *
   * <p>This ensures that no retained objects cause memory leaks.</p>
   *
   * @param ctx the Netty channel context
   */
  @Override
  public void handlerRemoved(final ChannelHandlerContext ctx) {
    for (Object message : this.queuedMessages) {
      ReferenceCountUtil.release(message);
    }

    this.queuedMessages.clear();
  }
}
