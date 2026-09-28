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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocityctd.api.event.connection.ConnectionEstablishEvent;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.event.VelocityEventManager;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.plugin.VelocityPluginManager;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.AutoReadHolderHandler;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintFrameDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftVarintLengthEncoder;
import com.velocitypowered.proxy.protocol.netty.PlayPacketQueueInboundHandler;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.HandshakePacket;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket;
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ConnectionTransitionTest {

  private static final ProtocolVersion VERSION = ProtocolVersion.MINECRAFT_1_21_4;

  private static void set(Class<?> type, Object target, String name,
      Object value) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static final class Sink extends ChannelInboundHandlerAdapter {
    final List<Object> seen = new ArrayList<>();
    int readCompletes;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      seen.add(msg);
      if ("pause".equals(msg)) {
        ctx.channel().config().setAutoRead(false);
      }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
      readCompletes++;
    }
  }

  @Test
  void theHolderStopsAtPauseAndKeepsTheRestInOrder() {
    Sink sink = new Sink();
    EmbeddedChannel channel = new EmbeddedChannel(new AutoReadHolderHandler(), sink);
    channel.config().setAutoRead(false);
    channel.writeInbound("a", "pause", "b", "c");
    assertTrue(sink.seen.isEmpty());
    channel.config().setAutoRead(true);
    channel.runPendingTasks();
    assertEquals(List.of("a", "pause"), sink.seen, "the pause holds b and c back");
    channel.config().setAutoRead(true);
    channel.runPendingTasks();
    assertEquals(List.of("a", "pause", "b", "c"), sink.seen);
  }

  @Test
  void readThatPausedMidwayStillCompletes() {
    Sink sink = new Sink();
    EmbeddedChannel channel = new EmbeddedChannel(new AutoReadHolderHandler(), sink);
    channel.writeInbound("a", "pause", "b");
    assertEquals(List.of("a", "pause"), sink.seen);
    assertEquals(1, sink.readCompletes, "what went through is flushed on");
  }

  @Test
  void backendAheadOfItsPlayerHoldsEverythingButLiveness() {
    Sink sink = new Sink();
    EmbeddedChannel channel = new EmbeddedChannel(
        PlayPacketQueueInboundHandler.forBackendAheadOfPlayer(), sink);
    PluginMessagePacket plugin = new PluginMessagePacket("test:channel", Unpooled.buffer());
    JoinGamePacket join = new JoinGamePacket();
    KeepAlivePacket keepAlive = new KeepAlivePacket();
    DisconnectPacket disconnect = mock(DisconnectPacket.class);
    channel.writeInbound(join, plugin, keepAlive, disconnect);
    assertEquals(List.of(keepAlive, disconnect), sink.seen,
        "the plugin message waits behind JoinGame");
    channel.pipeline().remove(PlayPacketQueueInboundHandler.class);
    assertEquals(List.of(keepAlive, disconnect, join, plugin), sink.seen);
  }

  @Test
  void theReconfigurationQueueStillLetsConfigPacketsThrough() {
    Sink sink = new Sink();
    EmbeddedChannel channel = new EmbeddedChannel(
        new PlayPacketQueueInboundHandler(VERSION, ProtocolUtils.Direction.CLIENTBOUND, false),
            sink);
    PluginMessagePacket plugin = new PluginMessagePacket("test:channel", Unpooled.buffer());
    channel.writeInbound(plugin);
    assertEquals(List.of(plugin), sink.seen, "unchanged for the client-side queue");
  }

  private VelocityServer server;
  private VelocityEventManager events;
  private CompletableFuture<Object> establish;

  private EmbeddedChannel clientChannel(boolean asyncListener) throws Exception {
    server = mock(VelocityServer.class);
    VelocityConfiguration config = mock(VelocityConfiguration.class);
    when(server.getConfiguration()).thenReturn(config);
    when(config.getMaximumVersion()).thenReturn(Optional.empty());
    when(config.getReadTimeout()).thenReturn(30000);
    events = mock(VelocityEventManager.class);
    when(server.getEventManager()).thenReturn(events);
    establish = new CompletableFuture<>();
    when(events.fire(any())).thenAnswer(invocation -> {
      Object event = invocation.getArgument(0);
      if (event instanceof ConnectionEstablishEvent) {
        return asyncListener ? establish : CompletableFuture.completedFuture(event);
      }
      return new CompletableFuture<>();
    });
    @SuppressWarnings("unchecked")
    Ratelimiter<InetAddress> limiter = mock(Ratelimiter.class);
    when(limiter.attempt(any())).thenReturn(true);
    when(server.getIpAttemptLimiter()).thenReturn(limiter);

    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addLast(Connections.FRAME_DECODER,
        new MinecraftVarintFrameDecoder(ProtocolUtils.Direction.SERVERBOUND));
    channel.pipeline().addLast(Connections.MINECRAFT_DECODER,
        new MinecraftDecoder(ProtocolUtils.Direction.SERVERBOUND));
    channel.pipeline().addLast(Connections.MINECRAFT_ENCODER,
        new MinecraftEncoder(ProtocolUtils.Direction.CLIENTBOUND));
    MinecraftConnection connection = new MinecraftConnection(channel, server, null);
    set(MinecraftConnection.class, connection, "remoteAddress", new InetSocketAddress("127.0.0.1",
        50000));
    channel.pipeline().addLast(Connections.HANDLER, connection);
    connection.setActiveSessionHandler(StateRegistry.HANDSHAKE, new HandshakeSessionHandler(
        connection, server));
    return channel;
  }

  private static ByteBuf encode(StateRegistry state, MinecraftPacket... packets) {
    EmbeddedChannel encoder = new EmbeddedChannel(MinecraftVarintLengthEncoder.INSTANCE,
        new MinecraftEncoder(ProtocolUtils.Direction.SERVERBOUND));
    MinecraftEncoder minecraftEncoder = encoder.pipeline().get(MinecraftEncoder.class);
    minecraftEncoder.setProtocolVersion(VERSION);
    minecraftEncoder.setState(state);
    ByteBuf out = Unpooled.buffer();
    for (MinecraftPacket packet : packets) {
      encoder.writeOutbound(packet);
      Object encoded;
      while ((encoded = encoder.readOutbound()) != null) {
        out.writeBytes((ByteBuf) encoded);
        ((ByteBuf) encoded).release();
      }
    }
    return out;
  }

  private static ByteBuf handshakeThenLogin(int extraLogins) {
    HandshakePacket handshake = new HandshakePacket();
    handshake.setProtocolVersion(VERSION);
    handshake.setServerAddress("play.example.com");
    handshake.setPort(25565);
    handshake.setIntent(HandshakeIntent.LOGIN);
    ByteBuf bytes = encode(StateRegistry.HANDSHAKE, handshake);
    for (int i = 0; i <= extraLogins; i++) {
      bytes.writeBytes(encode(StateRegistry.LOGIN, new ServerLoginPacket("Steve", UUID.randomUUID(
          ))));
    }
    return bytes;
  }

  private String preLoginUsername() {
    ArgumentCaptor<Object> fired = ArgumentCaptor.forClass(Object.class);
    Mockito.verify(events, Mockito.atLeastOnce()).fire(fired.capture());
    return fired.getAllValues().stream()
        .filter(PreLoginEvent.class::isInstance)
        .map(event -> ((PreLoginEvent) event).getUsername())
        .findFirst().orElse(null);
  }

  @Test
  void withoutAnAsyncListenerTheLoginIsHandledAsBefore() throws Exception {
    EmbeddedChannel channel = clientChannel(false);
    channel.writeInbound(handshakeThenLogin(0));
    channel.runPendingTasks();
    assertTrue(channel.isActive());
    assertEquals("Steve", preLoginUsername());
    assertTrue(channel.config().isAutoRead());
  }

  @Test
  void anAsyncListenerHoldsTheLoginAndReplaysItAfterDeciding() throws Exception {
    EmbeddedChannel channel = clientChannel(true);
    channel.writeInbound(handshakeThenLogin(0));
    channel.runPendingTasks();
    assertTrue(channel.isActive(), "the login start that came with the handshake is not refused");
    assertNull(preLoginUsername(), "nothing is handled before the listener decides");
    assertFalse(channel.config().isAutoRead());
    Thread listener = new Thread(() -> establish.complete(new ConnectionEstablishEvent(
        mock(InboundConnection.class), HandshakeIntent.LOGIN)));
    listener.start();
    listener.join();
    channel.runPendingTasks();
    assertTrue(channel.isActive());
    assertEquals("Steve", preLoginUsername(), "the held login start reaches the login handler");
    assertTrue(channel.config().isAutoRead());
  }

  @Test
  void deniedConnectionIsClosedWithItsHeldPackets() throws Exception {
    EmbeddedChannel channel = clientChannel(true);
    channel.writeInbound(handshakeThenLogin(0));
    ConnectionEstablishEvent event = new ConnectionEstablishEvent(
        mock(InboundConnection.class), HandshakeIntent.LOGIN);
    event.setResult(ResultedEvent.GenericResult.denied());
    establish.complete(event);
    channel.runPendingTasks();
    assertFalse(channel.isActive());
    assertNull(preLoginUsername());
  }

  @Test
  void clientSendingTooMuchBeforeTheDecisionIsClosed() throws Exception {
    EmbeddedChannel channel = clientChannel(true);
    channel.writeInbound(handshakeThenLogin(16));
    assertFalse(channel.isActive());
  }

  private ConnectedPlayer player;
  private VelocityServer playerServer;

  private ConnectedPlayer player() throws Exception {
    VelocityServer server = mock(VelocityServer.class, Mockito.RETURNS_DEEP_STUBS);
    playerServer = server;
    VelocityConfiguration config = mock(VelocityConfiguration.class);
    when(server.getConfiguration()).thenReturn(config);
    when(server.getEventManager()).thenReturn(mock(VelocityEventManager.class));
    VelocityPluginManager plugins = mock(VelocityPluginManager.class);
    PluginContainer container = mock(PluginContainer.class);
    when(server.getPluginManager()).thenReturn(plugins);
    when(plugins.ensurePluginContainer(any())).thenReturn(container);
    when(container.getExecutorService()).thenReturn(Executors.newSingleThreadExecutor());
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addLast(Connections.MINECRAFT_DECODER, new MinecraftDecoder(
        ProtocolUtils.Direction.SERVERBOUND));
    channel.pipeline().addLast(Connections.MINECRAFT_ENCODER, new MinecraftEncoder(
        ProtocolUtils.Direction.CLIENTBOUND));
    MinecraftConnection connection = new MinecraftConnection(channel, server, null);
    connection.setProtocolVersion(VERSION);
    channel.pipeline().addLast(Connections.HANDLER, connection);
    player = new ConnectedPlayer(server, new GameProfile(UUID.randomUUID(), "Steve", List.of()),
        connection, null, null, false, HandshakeIntent.LOGIN, null);
    connection.setAssociation(player);
    return player;
  }

  private VelocityServerConnection backend(StateRegistry state) throws Exception {
    VelocityServer server = mock(VelocityServer.class);
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addLast(Connections.MINECRAFT_DECODER, new MinecraftDecoder(
        ProtocolUtils.Direction.CLIENTBOUND));
    channel.pipeline().addLast(Connections.MINECRAFT_ENCODER, new MinecraftEncoder(
        ProtocolUtils.Direction.SERVERBOUND));
    MinecraftConnection connection = new MinecraftConnection(channel, server, null);
    channel.pipeline().addLast(Connections.HANDLER, connection);
    connection.setProtocolVersion(VERSION);
    connection.setState(state);
    VelocityRegisteredServer registered = mock(VelocityRegisteredServer.class);
    when(registered.getServerInfo()).thenReturn(new ServerInfo(
        "lobby", new InetSocketAddress("127.0.0.1", 30066)));
    VelocityServerConnection serverConnection =
        new VelocityServerConnection(registered, null, player, server);
    set(VelocityServerConnection.class, serverConnection, "connection", connection);
    return serverConnection;
  }

  private static VelocityServer unlistenedServer() {
    VelocityServer server = mock(VelocityServer.class, Mockito.RETURNS_DEEP_STUBS);
    when(server.getChannelRegistrar().getFromId(any())).thenReturn(null);
    return server;
  }

  private static Object get(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void handleAndRelease(ClientPlaySessionHandler handler,
                                       PluginMessagePacket packet) {
    handler.handle(packet);
    packet.release();
  }

  private static Object call(Object target, String method) throws Exception {
    Method m = target.getClass().getDeclaredMethod(method);
    m.setAccessible(true);
    return m.invoke(target);
  }

  @Test
  void reconfiguringConnectedServerIsTheConfigurationTarget() throws Exception {
    player();
    ClientConfigSessionHandler handler = new ClientConfigSessionHandler(mock(VelocityServer.class),
        player);
    assertNull(call(handler, "configurationTarget"));
    VelocityServerConnection connected = backend(StateRegistry.CONFIG);
    set(ConnectedPlayer.class, player, "connectedServer", connected);
    assertSame(connected, call(handler, "configurationTarget"),
        "it sent the player back to config");
    connected.ensureConnected().setState(StateRegistry.PLAY);
    assertNull(call(handler, "configurationTarget"),
        "a connected server in PLAY is not configuring");
    VelocityServerConnection inFlight = backend(StateRegistry.LOGIN);
    set(ConnectedPlayer.class, player, "connectionInFlight", inFlight);
    assertSame(inFlight, call(handler, "configurationTarget"));
  }

  @Test
  void playPluginMessageBetweenServersGoesToTheServerInFlight() throws Exception {
    player();
    ClientPlaySessionHandler handler = new ClientPlaySessionHandler(mock(VelocityServer.class),
        player);
    VelocityServerConnection inFlight = backend(StateRegistry.CONFIG);
    set(ConnectedPlayer.class, player, "connectionInFlight", inFlight);
    assertSame(inFlight, call(handler, "pluginMessageTarget"));
    VelocityServerConnection connected = backend(StateRegistry.PLAY);
    set(ConnectedPlayer.class, player, "connectedServer", connected);
    assertSame(connected, call(handler, "pluginMessageTarget"));
  }

  @Test
  void pluginMessageForServerStillJoiningWaitsForItsJoinGame() throws Exception {
    player();
    ClientPlaySessionHandler handler = new ClientPlaySessionHandler(unlistenedServer(), player);
    VelocityServerConnection inFlight = backend(StateRegistry.CONFIG);
    set(ConnectedPlayer.class, player, "connectionInFlight", inFlight);

    handleAndRelease(handler, new PluginMessagePacket("mod:hello",
        Unpooled.wrappedBuffer(new byte[] {1, 2, 3})));

    Queue<?> held = (Queue<?>) get(handler, "loginPluginMessages");
    assertEquals(1, held.size(), "held for the server's JoinGame instead of dropped");
    EmbeddedChannel backendChannel = (EmbeddedChannel) inFlight.ensureConnected().getChannel();
    assertNull(backendChannel.readOutbound(), "nothing reaches a server that is not in PLAY");
    held.forEach(ReferenceCountUtil::release);
  }

  @Test
  void pluginMessageForConnectedServerNotInPlayIsStillDiscarded() throws Exception {
    player();
    ClientPlaySessionHandler handler = new ClientPlaySessionHandler(unlistenedServer(), player);
    VelocityServerConnection connected = backend(StateRegistry.CONFIG);
    set(ConnectedPlayer.class, player, "connectedServer", connected);

    handleAndRelease(handler, new PluginMessagePacket("mod:hello",
        Unpooled.wrappedBuffer(new byte[] {1, 2, 3})));

    assertTrue(((Queue<?>) get(handler, "loginPluginMessages")).isEmpty());
  }

  @Test
  void clientSettingsDuringKickDoNotDisconnectThePlayer() throws Exception {
    player();
    ClientPlaySessionHandler handler = new ClientPlaySessionHandler(mock(VelocityServer.class),
        player);
    VelocityServerConnection kicked = backend(StateRegistry.PLAY);
    set(ConnectedPlayer.class, player, "connectedServer", kicked);
    set(VelocityServerConnection.class, kicked, "connection", null);
    assertTrue(handler.handle(new ClientSettingsPacket()));
  }

  private static int drainOutbound(EmbeddedChannel channel) {
    int count = 0;
    Object message;
    while ((message = channel.readOutbound()) != null) {
      ReferenceCountUtil.release(message);
      count++;
    }
    return count;
  }

  @Test
  void switchIsRequestedOnceUntilTheClientAcknowledgesIt() throws Exception {
    player();
    when(playerServer.getEventManager().fire(any()))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));
    EmbeddedChannel channel = (EmbeddedChannel) player.getConnection().getChannel();
    player.getConnection().setState(StateRegistry.PLAY);
    drainOutbound(channel);
    player.switchToConfigState();
    channel.runPendingTasks();
    assertEquals(1, drainOutbound(channel), "the first request is sent");
    player.switchToConfigState();
    channel.runPendingTasks();
    player.getConnection().setState(StateRegistry.PLAY);
    channel.runPendingTasks();
    assertEquals(0, drainOutbound(channel),
        "no second request is queued and then released once the client is back in play");
    player.getConnection().setState(StateRegistry.PLAY);

    ClientPlaySessionHandler handler = new ClientPlaySessionHandler(playerServer, player);
    set(ClientPlaySessionHandler.class, handler, "configSwitchFuture", new CompletableFuture<Void>(
        ));
    handler.handle(FinishedUpdatePacket.INSTANCE);
    assertFalse(player.getConnection().pendingConfigurationSwitch,
        "the acknowledgement settles it");
  }

  @Test
  void backendAdvancedAheadOfThePlayerHandsTheTimeoutBackToThePlayer() throws Exception {
    player();
    EmbeddedChannel channel = (EmbeddedChannel) player.getConnection().getChannel();
    channel.pipeline().addFirst(Connections.FRAME_DECODER,
        new MinecraftVarintFrameDecoder(ProtocolUtils.Direction.SERVERBOUND));
    assertNull(channel.pipeline().get(Connections.READ_TIMEOUT), "suspended while connecting");
    player.getConnection().setState(StateRegistry.CONFIG);
    VelocityServerConnection backend = backend(StateRegistry.CONFIG);
    Constructor<?> ctor = Class.forName(
        "com.velocitypowered.proxy.connection.backend.ConfigSessionHandler")
            .getDeclaredConstructors()[0];
    ctor.setAccessible(true);
    Object handler = ctor.newInstance(playerServer, backend, new CompletableFuture<>());
    Method advance = handler.getClass().getDeclaredMethod("advanceBackendToPlay", boolean.class);
    advance.setAccessible(true);
    advance.invoke(handler, true);
    assertTrue(channel.pipeline().get(Connections.READ_TIMEOUT) != null,
        "the proxy now keeps the backend alive, so the player's own timeout watches the player");
  }

  @Test
  void backendDecodesConfigurationUntilItsJoinGame() throws Exception {
    player();
    player.getConnection().setState(StateRegistry.CONFIG);
    VelocityServerConnection backend = backend(StateRegistry.CONFIG);
    Constructor<?> ctor = Class.forName(
        "com.velocitypowered.proxy.connection.backend.ConfigSessionHandler")
            .getDeclaredConstructors()[0];
    ctor.setAccessible(true);
    Object handler = ctor.newInstance(playerServer, backend, new CompletableFuture<>());
    Method advance = handler.getClass().getDeclaredMethod("advanceBackendToPlay", boolean.class);
    advance.setAccessible(true);

    advance.invoke(handler, false);

    MinecraftDecoder decoder =
        backend.ensureConnected().getChannel().pipeline().get(MinecraftDecoder.class);
    assertSame(StateRegistry.CONFIG, get(decoder, "state"),
        "the backend is still in CONFIG until it reads the acknowledgement");
    assertSame(StateRegistry.PLAY, get(decoder, "awaitedState"));
  }

  @Test
  void refusedTransferIsToldWhy() throws Exception {
    final EmbeddedChannel channel = clientChannel(false);
    HandshakePacket handshake = new HandshakePacket();
    handshake.setProtocolVersion(VERSION);
    handshake.setServerAddress("play.example.com");
    handshake.setPort(25565);
    handshake.setIntent(HandshakeIntent.TRANSFER);
    channel.writeInbound(encode(StateRegistry.HANDSHAKE, handshake));
    channel.runPendingTasks();
    ByteBuf disconnect = channel.readOutbound();
    assertTrue(disconnect != null, "a login disconnect is written, not just a close");
    assertEquals(0x00, disconnect.getByte(disconnect.readerIndex()), "the LOGIN disconnect id");
    disconnect.release();
  }
}
