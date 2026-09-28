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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class AutoReadHolderHandlerTest {

  @Test
  void holdsMessagesWhilePausedAndReleasesThemOnResume() {
    EmbeddedChannel channel = new EmbeddedChannel(new AutoReadHolderHandler());
    channel.config().setAutoRead(false);

    channel.writeInbound("first", "second");
    assertNull(channel.readInbound());

    channel.config().setAutoRead(true);
    assertEquals("first", channel.readInbound());
    assertEquals("second", channel.readInbound());
    channel.finishAndReleaseAll();
  }

  @Test
  void letsDisconnectThroughWhilePaused() {
    EmbeddedChannel channel = new EmbeddedChannel(new AutoReadHolderHandler());
    channel.config().setAutoRead(false);
    DisconnectPacket disconnect = new DisconnectPacket(StateRegistry.PLAY);

    channel.writeInbound("held", disconnect);
    assertSame(disconnect, channel.readInbound());
    assertNull(channel.readInbound());
    channel.finishAndReleaseAll();
  }
}
