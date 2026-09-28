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

package com.velocitypowered.proxy.connection.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.network.netty.VelocityReadTimeoutHandler;
import com.velocitypowered.proxy.plugin.VelocityPluginManager;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.packet.ServerLoginSuccessPacket;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import io.netty.buffer.Unpooled;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class BackpressureTimeoutTest {

  private static final int WRITE_TIMEOUT = 5000;
  private static final int BACKEND_TIMEOUT = 1000;

  private VelocityServer server;
  private EmbeddedChannel playerChannel;
  private MinecraftConnection playerConnection;
  private ConnectedPlayer player;
  private EmbeddedChannel backendChannel;
  private MinecraftConnection backendConnection;
  private final List<EmbeddedChannel> channels = new ArrayList<>();

  private void setUp(int writeTimeout, boolean clientReadTimeout) throws Exception {
    server = mock(VelocityServer.class);
    VelocityConfiguration config = mock(VelocityConfiguration.class);
    when(server.getConfiguration()).thenReturn(config);
    when(config.getReadTimeout()).thenReturn(writeTimeout);
    VelocityPluginManager plugins = mock(VelocityPluginManager.class);
    PluginContainer container = mock(PluginContainer.class);
    when(server.getPluginManager()).thenReturn(plugins);
    when(plugins.ensurePluginContainer(ArgumentMatchers.any())).thenReturn(container);
    when(container.getExecutorService()).thenReturn(Executors.newSingleThreadExecutor());

    playerChannel = new EmbeddedChannel();
    playerChannel.freezeTime();
    playerChannel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(8, 16));
    if (clientReadTimeout) {
      playerChannel.pipeline().addLast(Connections.READ_TIMEOUT,
          new VelocityReadTimeoutHandler(BACKEND_TIMEOUT, TimeUnit.MILLISECONDS));
    }
    playerChannel.pipeline().addLast("decoder", new MinecraftDecoder(
        ProtocolUtils.Direction.SERVERBOUND));
    playerChannel.pipeline().addLast("encoder", new MinecraftEncoder(
        ProtocolUtils.Direction.CLIENTBOUND));
    playerConnection = new MinecraftConnection(playerChannel, server, null);
    playerConnection.setProtocolVersion(ProtocolVersion.MINECRAFT_1_21_4);
    playerConnection.setState(StateRegistry.PLAY);
    playerChannel.pipeline().addLast(Connections.HANDLER, playerConnection);
    player = new ConnectedPlayer(server, new GameProfile(UUID.randomUUID(), "Steve", List.of()),
        playerConnection, null, null, false, HandshakeIntent.LOGIN, null);
    playerConnection.setAssociation(player);
    channels.add(playerChannel);

    backendChannel = newBackend(true);
    backendConnection = backendChannel.pipeline().get(MinecraftConnection.class);
    set(ConnectedPlayer.class, player, "connectedServer", backendConnection.getAssociation());
  }

  private EmbeddedChannel newBackend(boolean readTimeout) throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.freezeTime();
    channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(8, 16));
    if (readTimeout) {
      channel.pipeline().addLast(Connections.READ_TIMEOUT,
          new VelocityReadTimeoutHandler(BACKEND_TIMEOUT, TimeUnit.MILLISECONDS));
    }
    MinecraftConnection connection = new MinecraftConnection(channel, server, null);
    channel.pipeline().addLast(Connections.HANDLER, connection);
    VelocityServerConnection serverConnection =
        new VelocityServerConnection(mock(VelocityRegisteredServer.class), null, player, server);
    connection.setAssociation(serverConnection);
    set(VelocityServerConnection.class, serverConnection, "connection", connection);
    channels.add(channel);
    return channel;
  }

  private static void set(Class<?> type, Object target, String name,
      Object value) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void fill(EmbeddedChannel channel) {
    channel.write(Unpooled.wrappedBuffer(new byte[64]));
    channel.runPendingTasks();
    assertFalse(channel.isWritable());
  }

  private static void drain(EmbeddedChannel channel) {
    channel.flush();
    channel.runPendingTasks();
    assertTrue(channel.isWritable());
  }

  private final long[] clock = {0};

  private void stepTo(long millis) {
    while (clock[0] < millis) {
      clock[0]++;
      for (EmbeddedChannel channel : channels) {
        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        channel.runPendingTasks();
        channel.runScheduledPendingTasks();
      }
    }
  }

  @Test
  void quietBackendStillTimesOutWithoutPause() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    stepTo(1000);
    assertTrue(backendChannel.isActive());
    stepTo(1003);
    assertFalse(backendChannel.isActive(), "control: the read timeout is unchanged");
  }

  @Test
  void shortPauseDelaysTheTimeoutByItsLengthOnly() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    stepTo(100);
    fill(playerChannel);
    backendConnection.setPausedForBackpressure(true);
    stepTo(600);
    drain(playerChannel);
    backendConnection.setPausedForBackpressure(false);
    stepTo(1499);
    assertTrue(backendChannel.isActive(), "1000 of listening is not over before 1500");
    stepTo(1503);
    assertFalse(backendChannel.isActive(), "due at 1500, not a whole timeout later");
  }

  @Test
  void pauseForUnwritablePlayerIsLeftOutOfTheBackendTimeout() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    stepTo(100);
    fill(playerChannel);
    backendConnection.setPausedForBackpressure(true);
    stepTo(2500);
    assertTrue(backendChannel.isActive(), "not timed out while paused for the player");
    drain(playerChannel);
    backendConnection.setPausedForBackpressure(false);
    stepTo(3399);
    assertTrue(backendChannel.isActive(), "100 before the pause + 900 after is not 1000 yet");
    stepTo(3403);
    assertFalse(backendChannel.isActive(), "a backend silent after the pause times out on time");
  }

  @Test
  void readRestartsThePausedCount() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    stepTo(100);
    fill(playerChannel);
    backendConnection.setPausedForBackpressure(true);
    stepTo(2500);
    drain(playerChannel);
    backendConnection.setPausedForBackpressure(false);
    stepTo(2600);
    backendChannel.writeInbound(Unpooled.wrappedBuffer(new byte[] {1}));
    stepTo(3599);
    assertTrue(backendChannel.isActive());
    stepTo(3603);
    assertFalse(backendChannel.isActive(), "the pause before the read no longer counts");
  }

  @Test
  void unwritablePlayerIsWriteTimedOutAndTheBackendIsNot() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    stepTo(100);
    fill(playerChannel);
    backendConnection.setPausedForBackpressure(true);
    stepTo(5100);
    assertTrue(playerChannel.isActive(), "confirmed on a later pass first");
    assertTrue(backendChannel.isActive());
    stepTo(5102);
    assertFalse(playerChannel.isActive(), "the player who took nothing for the timeout is closed");
  }

  @Test
  void socketThatDrainedBeforeTheConfirmationIsKept() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    fill(playerChannel);
    stepTo(5000);
    drain(playerChannel);
    stepTo(5010);
    assertTrue(playerChannel.isActive());
  }

  @Test
  void drainingInTimeKeepsThePlayerAndNextEpisodeStartsNewClock() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    fill(playerChannel);
    stepTo(4000);
    drain(playerChannel);
    fill(playerChannel);
    stepTo(8000);
    assertTrue(playerChannel.isActive(), "4000 + 4000 across two episodes is not one of 5000");
    stepTo(9002);
    assertFalse(playerChannel.isActive());
  }

  @Test
  void zeroTimeoutNeverWriteTimesOut() throws Exception {
    setUp(0, false);
    backendChannel.pipeline().remove(Connections.READ_TIMEOUT);
    fill(playerChannel);
    stepTo(20_000);
    assertTrue(playerChannel.isActive());
  }

  @Test
  void pauseNothingResumedIsNotLeftOut() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    stepTo(100);
    backendConnection.setPausedForBackpressure(true);
    stepTo(1003);
    assertFalse(backendChannel.isActive(), "the player can take writes, so the pause is no excuse");
  }

  @Test
  void replacedTimeoutKeepsThePauseInProgress() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    stepTo(100);
    fill(playerChannel);
    backendConnection.setPausedForBackpressure(true);
    stepTo(500);
    backendChannel.pipeline().replace(Connections.READ_TIMEOUT, Connections.READ_TIMEOUT,
        new VelocityReadTimeoutHandler(BACKEND_TIMEOUT, TimeUnit.MILLISECONDS));
    stepTo(3000);
    assertTrue(backendChannel.isActive(), "the new handler knows reading is paused");
  }

  @Test
  void pauseForUnwritableBackendIsLeftOutOfThePlayerTimeout() throws Exception {
    setUp(WRITE_TIMEOUT, true);
    backendChannel.pipeline().remove(Connections.READ_TIMEOUT);
    stepTo(100);
    fill(backendChannel);
    playerConnection.setPausedForBackpressure(true);
    stepTo(3000);
    assertTrue(playerChannel.isActive(), "the player is not timed out for their backend");
    stepTo(5102);
    assertFalse(backendChannel.isActive(), "the backend that took nothing is closed");
  }

  @Test
  void closedBackendResumesThePlayerPausedForIt() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    fill(backendChannel);
    playerConnection.setPausedForBackpressure(true);
    assertFalse(playerChannel.config().isAutoRead());
    backendChannel.close();
    playerChannel.runPendingTasks();
    assertTrue(playerChannel.config().isAutoRead(), "Netty reports no writability change on close");
    assertFalse(playerConnection.isPausedForBackpressure());
  }

  @Test
  void closedBackendKeepsThePauseWhileAnotherStillCannotTakeWrites() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    EmbeddedChannel inFlight = newBackend(false);
    set(ConnectedPlayer.class, player, "connectionInFlight",
        inFlight.pipeline().get(MinecraftConnection.class).getAssociation());
    fill(backendChannel);
    fill(inFlight);
    playerConnection.setPausedForBackpressure(true);
    backendChannel.close();
    playerChannel.runPendingTasks();
    assertFalse(playerChannel.config().isAutoRead(),
        "the in-flight backend still cannot take writes");
  }

  @Test
  void everyBackendIsPausedWhileThePlayerIsBehind() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    EmbeddedChannel inFlight = newBackend(false);
    set(ConnectedPlayer.class, player, "connectionInFlight",
        inFlight.pipeline().get(MinecraftConnection.class).getAssociation());
    fill(playerChannel);
    assertFalse(backendChannel.config().isAutoRead());
    assertFalse(inFlight.config().isAutoRead(), "the in-flight backend relays into the player too");
    drain(playerChannel);
    assertTrue(backendChannel.config().isAutoRead());
    assertTrue(inFlight.config().isAutoRead());
  }

  @Test
  void protocolResumeKeepsBackendPausedForThePlayer() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    fill(playerChannel);
    backendConnection.setAutoReading(false);
    backendConnection.setAutoReading(true);
    assertFalse(backendChannel.config().isAutoRead(), "the player still cannot take writes");
    assertTrue(backendConnection.isPausedForBackpressure());
    drain(playerChannel);
    assertTrue(backendChannel.config().isAutoRead());
  }

  @Test
  void backpressureReleaseKeepsProtocolPause() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    backendConnection.setAutoReading(false);
    fill(playerChannel);
    drain(playerChannel);
    assertFalse(backendChannel.config().isAutoRead(), "the protocol step still holds it");
    backendConnection.setAutoReading(true);
    assertTrue(backendChannel.config().isAutoRead());
  }

  @Test
  void backendConnectingWhileThePlayerIsBehindStartsPaused() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    fill(playerChannel);
    EmbeddedChannel late = newBackend(true);
    assertFalse(late.config().isAutoRead());
    assertTrue(late.pipeline().get(MinecraftConnection.class).isPausedForBackpressure());
  }

  @Test
  void writeTimeoutDuringKnownDisconnectJustCloses() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    set(MinecraftConnection.class, playerConnection, "knownDisconnect", true);
    fill(playerChannel);
    stepTo(5000);
    assertFalse(playerChannel.isActive(), "already closing, so no confirmation or exception");
  }

  @Test
  void resumeBetweenChecksIsCheckedAtTheDueTime() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    stepTo(900);
    fill(playerChannel);
    stepTo(1500);
    drain(playerChannel);
    stepTo(1599);
    assertTrue(backendChannel.isActive(), "900 before the pause + 99 after");
    stepTo(1603);
    assertFalse(backendChannel.isActive(), "due at 1600, before Netty's own check at 2000");
  }

  @Test
  void pauseNoWriteTimeoutBoundsIsNotLeftOut() throws Exception {
    setUp(0, false);
    stepTo(100);
    fill(playerChannel);
    assertTrue(backendConnection.isPausedForBackpressure());
    stepTo(1003);
    assertFalse(backendChannel.isActive(), "with write timeouts off, the pause could last forever");
  }

  @Test
  void configurationPhaseStartedAgainCanAdvanceTheBackendAgain() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    Constructor<?> ctor = Class.forName(
        "com.velocitypowered.proxy.connection.backend.ConfigSessionHandler")
            .getDeclaredConstructors()[0];
    ctor.setAccessible(true);
    Object handler = ctor.newInstance(server, backendConnection.getAssociation(),
        new CompletableFuture<>());
    Field advanced = handler.getClass().getDeclaredField("backendAdvancedToPlay");
    advanced.setAccessible(true);
    advanced.set(handler, true);
    ((MinecraftSessionHandler) handler).activated();
    assertFalse((boolean) advanced.get(handler), "the reused handler serves a new phase");
  }

  @Test
  void backendThatLoggedInGetsTheReadTimeout() throws Exception {
    setUp(WRITE_TIMEOUT, false);
    Constructor<?> ctor = Class.forName(
        "com.velocitypowered.proxy.connection.backend.LoginSessionHandler").getDeclaredConstructors(
            )[0];
    ctor.setAccessible(true);
    Object handler = ctor.newInstance(server, backendConnection.getAssociation(),
        new CompletableFuture<>());
    try {
      ((MinecraftSessionHandler) handler)
          .handle(new ServerLoginSuccessPacket());
    } catch (RuntimeException ignored) {
      // The mocked player cannot finish the login; only the timeout swap before it is checked.
    }
    VelocityReadTimeoutHandler timeout =
        (VelocityReadTimeoutHandler) backendChannel.pipeline().get(Connections.READ_TIMEOUT);
    assertEquals(WRITE_TIMEOUT, timeout.getReaderIdleTimeInMillis(),
        "login is done, so the login-timeout no longer applies");
  }
}
