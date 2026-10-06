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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InboundHoldHandlerTest {

  private static final String HOLD = "hold";

  private final List<Object> received = new ArrayList<>();
  private int readsCompleted;

  private EmbeddedChannel channel() {
    return new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) {
        received.add(msg);
      }

      @Override
      public void channelReadComplete(ChannelHandlerContext ctx) {
        readsCompleted++;
      }
    });
  }

  @Test
  void heldMessagesArePassedOnInOrderAsOneReadWhenRemoved() {
    EmbeddedChannel channel = channel();
    channel.pipeline().addFirst(HOLD, new InboundHoldHandler());
    ByteBuf first = Unpooled.buffer().writeByte(1);
    ByteBuf second = Unpooled.buffer().writeByte(2);

    channel.pipeline().fireChannelRead(first).fireChannelRead(second).fireChannelReadComplete();
    assertEquals(List.of(), received);
    assertEquals(0, readsCompleted);

    channel.pipeline().remove(HOLD);
    assertEquals(2, received.size());
    assertSame(first, received.get(0));
    assertSame(second, received.get(1));
    assertEquals(1, readsCompleted);

    first.release();
    second.release();
    channel.finishAndReleaseAll();
  }

  @Test
  void nothingIsFiredWhenNothingWasHeld() {
    EmbeddedChannel channel = channel();
    channel.pipeline().addFirst(HOLD, new InboundHoldHandler());

    channel.pipeline().fireChannelReadComplete();
    assertEquals(1, readsCompleted, "a read with nothing held completes as usual");

    channel.pipeline().remove(HOLD);
    assertEquals(List.of(), received);
    assertEquals(1, readsCompleted);
    channel.finishAndReleaseAll();
  }

  @Test
  void heldMessagesAreReleasedWhenTheChannelCloses() {
    EmbeddedChannel channel = channel();
    channel.pipeline().addFirst(HOLD, new InboundHoldHandler());
    ByteBuf held = Unpooled.buffer().writeByte(1);

    channel.pipeline().fireChannelRead(held);
    channel.close();

    assertEquals(0, held.refCnt());
    assertEquals(List.of(), received);
    channel.finishAndReleaseAll();
  }
}
