/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.cluster.VelocityClusterPlayerService;
import com.velocityctd.proxy.util.SamplePlayersPicker.Ordering;
import com.velocitypowered.proxy.VelocityServer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The server list ping is answered on a network thread and needs no login, so the players it
 * samples come from the last cluster sync rather than a Redis read per ping.
 */
class SamplePlayersPickerTest {

  @Test
  void serverListSampleReadsTheLastSync() {
    VelocityServer server = mock(VelocityServer.class);
    VelocityClusterPlayerService cluster = mock(VelocityClusterPlayerService.class);
    when(server.getClusterPlayerService()).thenReturn(cluster);
    VelocityClusterPlayer player = mock(VelocityClusterPlayer.class);
    when(cluster.getPlayersAsOfLastSync()).thenReturn(List.of(player));

    List<VelocityClusterPlayer> sample =
        SamplePlayersPicker.create(server).samplePlayers(12, Ordering.RANDOM);

    assertEquals(List.of(player), sample);
    verify(cluster, never()).getAllPlayers();
  }
}
