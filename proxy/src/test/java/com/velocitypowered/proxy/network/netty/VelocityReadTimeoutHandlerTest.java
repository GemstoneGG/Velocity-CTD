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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class VelocityReadTimeoutHandlerTest {

  private static EmbeddedChannel channel(ChannelHandler handler) {
    EmbeddedChannel ch = new EmbeddedChannel(handler);
    ch.freezeTime();
    return ch;
  }

  /** The loop was blocked past the timeout; the peer's data is read only after the idle check. */
  private static void holdUpLoopThenDeliverLate(EmbeddedChannel ch) {
    ch.advanceTimeBy(11, TimeUnit.SECONDS);
    ch.runScheduledPendingTasks();
    if (ch.isOpen()) {
      ch.writeInbound("sent while the loop was held up");
      ch.advanceTimeBy(5, TimeUnit.MILLISECONDS);
      ch.runScheduledPendingTasks();
    }
  }

  @Test
  void nettyHandlerDropsPeerThatSentWhileLoopWasHeldUp() {
    EmbeddedChannel ch = channel(new ReadTimeoutHandler(10, TimeUnit.SECONDS));
    holdUpLoopThenDeliverLate(ch);
    assertFalse(ch.isOpen(), "model reproduces the false timeout");
  }

  @Test
  void keepsPeerThatSentWhileLoopWasHeldUp() {
    EmbeddedChannel ch = channel(new VelocityReadTimeoutHandler(10, TimeUnit.SECONDS));
    holdUpLoopThenDeliverLate(ch);
    assertTrue(ch.isOpen());
    ch.checkException();
  }

  @Test
  void timesOutSilentPeer() {
    EmbeddedChannel ch = channel(new VelocityReadTimeoutHandler(10, TimeUnit.SECONDS));
    ch.advanceTimeBy(11, TimeUnit.SECONDS);
    ch.runScheduledPendingTasks();
    assertTrue(ch.isOpen(), "only suspected so far");
    ch.advanceTimeBy(5, TimeUnit.MILLISECONDS);
    ch.runScheduledPendingTasks();
    assertFalse(ch.isOpen());
    assertThrows(ReadTimeoutException.class, ch::checkException);
  }

  @Test
  void stillTimesOutLaterAfterFalseAlarm() {
    EmbeddedChannel ch = channel(new VelocityReadTimeoutHandler(10, TimeUnit.SECONDS));
    holdUpLoopThenDeliverLate(ch);
    assertTrue(ch.isOpen());
    ch.advanceTimeBy(11, TimeUnit.SECONDS);
    ch.runScheduledPendingTasks();
    ch.advanceTimeBy(5, TimeUnit.MILLISECONDS);
    ch.runScheduledPendingTasks();
    assertFalse(ch.isOpen());
    assertThrows(ReadTimeoutException.class, ch::checkException);
  }

  @Test
  void activePeerIsNeverSuspected() {
    EmbeddedChannel ch = channel(new VelocityReadTimeoutHandler(10, TimeUnit.SECONDS));
    for (int i = 0; i < 100; i++) {
      ch.advanceTimeBy(1, TimeUnit.SECONDS);
      ch.writeInbound("tick");
      ch.runScheduledPendingTasks();
    }
    assertTrue(ch.isOpen());
  }

  @Test
  void removedHandlerNeverClosesTheChannel() {
    VelocityReadTimeoutHandler handler = new VelocityReadTimeoutHandler(10, TimeUnit.SECONDS);
    EmbeddedChannel ch = channel(handler);
    ch.advanceTimeBy(11, TimeUnit.SECONDS);
    ch.runScheduledPendingTasks();
    ch.pipeline().remove(handler);
    ch.advanceTimeBy(5, TimeUnit.MILLISECONDS);
    ch.runScheduledPendingTasks();
    assertTrue(ch.isOpen(), "a replaced or paused timeout must not fire later");
  }
}
