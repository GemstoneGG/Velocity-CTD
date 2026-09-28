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

package com.velocitypowered.proxy.connection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ReconfigurationQueueTest {

  @Test
  void playerReenteringConfigurationDropsStalePlayPackets() throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel();
    MinecraftConnection connection = connection(channel);
    connection.setState(StateRegistry.PLAY);
    connection.setAssociation(mock(ConnectedPlayer.class));
    connection.pendingConfigurationSwitch = false;

    connection.setState(StateRegistry.CONFIG);

    assertTrue(discardsStaleInbound(channel));
  }

  @Test
  void playerEnteringConfigurationFromLoginKeepsItsPackets() throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel();
    MinecraftConnection connection = connection(channel);
    connection.setState(StateRegistry.LOGIN);
    connection.setAssociation(mock(ConnectedPlayer.class));

    connection.setState(StateRegistry.CONFIG);

    assertFalse(discardsStaleInbound(channel));
  }

  private static MinecraftConnection connection(EmbeddedChannel channel) {
    channel.pipeline().addLast(Connections.MINECRAFT_DECODER,
        new MinecraftDecoder(ProtocolUtils.Direction.SERVERBOUND));
    channel.pipeline().addLast(Connections.MINECRAFT_ENCODER,
        new MinecraftEncoder(ProtocolUtils.Direction.CLIENTBOUND));
    MinecraftConnection connection =
        new MinecraftConnection(channel, mock(VelocityServer.class), null);
    channel.pipeline().addLast(Connections.HANDLER, connection);
    connection.setProtocolVersion(ProtocolVersion.MAXIMUM_VERSION);
    return connection;
  }

  private static boolean discardsStaleInbound(EmbeddedChannel channel) throws Exception {
    Object queue = channel.pipeline().get(Connections.PLAY_PACKET_QUEUE_INBOUND);
    Field field = queue.getClass().getDeclaredField("discardStaleInbound");
    field.setAccessible(true);
    return field.getBoolean(queue);
  }
}
