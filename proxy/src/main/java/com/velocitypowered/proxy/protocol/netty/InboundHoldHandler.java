/*
 * Copyright (C) 2026 Velocity Contributors
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;

/**
 * Holds every inbound message that reaches it and passes them on, in order, once it is removed.
 *
 * <p>It sits in front of the {@link MinecraftDecoder} while a protocol step writes a packet that
 * asks the peer to change state and then switches to that state itself. A handler in the pipeline
 * may answer that packet before its write returns, and the answer has to be decoded and handled in
 * the state the step switches to, not the one it is leaving.
 */
public class InboundHoldHandler extends ChannelInboundHandlerAdapter {

  private final Queue<Object> held = new ArrayDeque<>();

  @Override
  public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) {
    this.held.add(msg);
  }

  @Override
  public void channelReadComplete(@NotNull ChannelHandlerContext ctx) {
    // Fired again with the held messages, so they are handled as one read.
    if (this.held.isEmpty()) {
      ctx.fireChannelReadComplete();
    }
  }

  @Override
  public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
    this.releaseHeld();

    super.channelInactive(ctx);
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) {
    if (!ctx.channel().isActive()) {
      this.releaseHeld();
      return;
    }
    if (this.held.isEmpty()) {
      return;
    }

    Object msg;
    while ((msg = this.held.poll()) != null) {
      ctx.fireChannelRead(msg);
    }
    ctx.fireChannelReadComplete();
  }

  private void releaseHeld() {
    Object msg;
    while ((msg = this.held.poll()) != null) {
      ReferenceCountUtil.release(msg);
    }
  }
}
