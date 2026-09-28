/*
 * Copyright (C) 2020-2023 Velocity Contributors
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

import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
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
  private boolean deliveredSinceReadComplete;

  public AutoReadHolderHandler() {
    this.queuedMessages = new ArrayDeque<>();
  }

  @Override
  public void read(ChannelHandlerContext ctx) throws Exception {
    if (drainQueuedMessages(ctx)) {
      ctx.read();
    }
  }

  /**
   * Releases held messages in order for as long as the channel is auto-reading. A message handled
   * here can pause reading again (backpressure, a protocol step), and the rest then wait for the
   * next read rather than being pushed past the pause.
   *
   * @param ctx this handler's context
   * @return whether no held messages are left
   */
  private boolean drainQueuedMessages(ChannelHandlerContext ctx) {
    if (!this.queuedMessages.isEmpty()) {
      Object queued;
      while (ctx.channel().config().isAutoRead() && (queued = this.queuedMessages.poll()) != null) {
        ctx.fireChannelRead(queued);
      }
      this.deliveredSinceReadComplete = false;
      ctx.fireChannelReadComplete();
    }
    return this.queuedMessages.isEmpty();
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, @NotNull Object msg) {
    // A disconnect ends the connection, so it is never held: epoll reads the peer's close even
    // while reading is paused, and a held disconnect would be released unread, losing its reason.
    if (msg instanceof DisconnectPacket
        || (ctx.channel().config().isAutoRead() && this.queuedMessages.isEmpty())) {
      this.deliveredSinceReadComplete = true;
      ctx.fireChannelRead(msg);
    } else {
      this.queuedMessages.add(msg);
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    if (ctx.channel().config().isAutoRead() && !this.queuedMessages.isEmpty()) {
      this.drainQueuedMessages(ctx); // will also call fireChannelReadComplete()
    } else if (ctx.channel().config().isAutoRead() || this.deliveredSinceReadComplete) {
      // Messages of this read went through before a pause held the rest, so they still complete
      // (and get flushed on), rather than waiting for reading to resume.
      this.deliveredSinceReadComplete = false;
      ctx.fireChannelReadComplete();
    }
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) {
    for (Object message : this.queuedMessages) {
      ReferenceCountUtil.release(message);
    }
    this.queuedMessages.clear();
  }
}
