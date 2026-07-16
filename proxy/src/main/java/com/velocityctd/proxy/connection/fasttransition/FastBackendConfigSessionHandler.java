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

package com.velocityctd.proxy.connection.fasttransition;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.backend.ConfigSessionHandler;
import com.velocitypowered.proxy.connection.backend.TransitionSessionHandler;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.ClientboundCookieRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundStoreCookiePacket;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.RemoveResourcePackPacket;
import com.velocitypowered.proxy.protocol.packet.ResourcePackRequestPacket;
import com.velocitypowered.proxy.protocol.packet.TransferPacket;
import com.velocitypowered.proxy.protocol.packet.config.CodeOfConductPacket;
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket;
import com.velocitypowered.proxy.protocol.packet.config.KnownPacksPacket;
import com.velocitypowered.proxy.protocol.packet.config.RegistrySyncPacket;
import com.velocitypowered.proxy.protocol.packet.config.TagsUpdatePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Backend-side configuration handler for the Velocity-CTD fast-transition path.
 *
 * <p>Unlike {@link ConfigSessionHandler}, this handler drives the target backend through
 * configuration without pushing the client into CONFIG: the client stays in PLAY on its current
 * server. Every clientbound config packet is buffered and fingerprinted, then compared against what
 * the client holds once the backend finishes:</p>
 *
 * <ul>
 *   <li><b>Compatible</b> - the buffered config is discarded and the backend is advanced to
 *       PLAY behind a {@link TransitionSessionHandler}; the client is switched entirely in PLAY.</li>
 *   <li><b>Incompatible</b> (or not fast-trackable) - falls back to a standard
 *       reconfiguration via a fresh {@link ConfigSessionHandler}, replaying the buffered packets so
 *       the outcome matches the normal switch.</li>
 * </ul>
 */
public class FastBackendConfigSessionHandler implements MinecraftSessionHandler {

  private static final Logger LOGGER = LogManager.getLogger(FastBackendConfigSessionHandler.class);

  private final VelocityServer server;
  private final VelocityServerConnection serverConn;
  private final CompletableFuture<Impl> resultFuture;

  private final ConfigStateSnapshot.Builder snapshot;

  // Clientbound config packets, retained in arrival order, replayed to the client only on fallback.
  private final List<MinecraftPacket> buffered = new ArrayList<>();

  // Cleared when the backend sends config packets requiring client interaction (resource packs,
  // cookies, transfers, code of conduct), which forces the fallback reconfiguration.
  private boolean fastTrackable = true;

  private boolean decided;

  public FastBackendConfigSessionHandler(VelocityServer server, VelocityServerConnection serverConn,
                                         CompletableFuture<Impl> resultFuture) {
    this.server = server;
    this.serverConn = serverConn;
    this.resultFuture = resultFuture;
    this.snapshot = ConfigStateSnapshot.builder("backend-" + serverConn.getServerInfo().getName());
  }

  @Override
  public boolean beforeHandle() {
    if (!serverConn.isActive()) {
      serverConn.disconnect();
      return true;
    }

    return false;
  }

  @Override
  public boolean handle(KeepAlivePacket packet) {
    // The client is in PLAY on another server, so answer the backend's config keepalive ourselves.
    serverConn.ensureConnected().write(packet);
    return true;
  }

  @Override
  public boolean handle(KnownPacksPacket packet) {
    // Reply with the packs the client advertised during its initial configuration, so the backend
    // produces the same registry data the client holds; echoing the backend's own offer would be
    // unsafe if it advertises packs the client never had.
    List<KnownPacksPacket.KnownPack> clientKnown = serverConn.getPlayer().getClientKnownPacks();
    serverConn.ensureConnected().write(
        clientKnown != null ? new KnownPacksPacket(clientKnown) : packet);

    // Buffer the offer: on fallback the client needs it to resolve the registry entries the backend
    // omitted for known packs. The client's response is dropped so the backend isn't answered twice.
    bufferRetained(packet);
    return true;
  }

  @Override
  public boolean handle(RegistrySyncPacket packet) {
    snapshot.addRegistrySync(packet.content(), serverConn.getPlayer().getProtocolVersion());
    bufferRetained(packet);
    return true;
  }

  @Override
  public boolean handle(TagsUpdatePacket packet) {
    snapshot.addTags(packet.getTags());
    bufferRetained(packet);
    return true;
  }

  @Override
  public boolean handle(FinishedUpdatePacket packet) {
    decide();
    return true;
  }

  @Override
  public boolean handle(DisconnectPacket packet) {
    serverConn.disconnect();
    if (serverConn.getPlayer().getConnectionInFlight() != null) {
      resultFuture.complete(ConnectionRequestResults.forDisconnect(packet, serverConn.getServer()));
    } else {
      serverConn.getPlayer().handleConnectionException(serverConn.getServer(), packet, true);
    }
    return true;
  }

  @Override
  public void handleGeneric(MinecraftPacket packet) {
    if (packet instanceof ResourcePackRequestPacket
        || packet instanceof RemoveResourcePackPacket
        || packet instanceof ClientboundStoreCookiePacket
        || packet instanceof ClientboundCookieRequestPacket
        || packet instanceof TransferPacket
        || packet instanceof CodeOfConductPacket) {
      // Needs the client to respond in CONFIG; can't be done while it's held in PLAY.
      fastTrackable = false;
    }
    bufferRetained(packet);
  }

  private void bufferRetained(MinecraftPacket packet) {
    ReferenceCountUtil.retain(packet);
    buffered.add(packet);
  }

  /**
   * Decides between the fast in-PLAY switch and the fallback reconfiguration once the backend has
   * finished sending its configuration.
   */
  private void decide() {
    if (decided) {
      return;
    }
    decided = true;

    ConnectedPlayer player = serverConn.getPlayer();
    ClientPlaySessionHandler clientPlay =
        player.getConnection().getActiveSessionHandler() instanceof ClientPlaySessionHandler h
            ? h : null;
    ConfigStateSnapshot backendSnapshot = snapshot.build();
    ConfigStateSnapshot clientSnapshot = player.getClientConfigSnapshot();
    boolean compatible = backendSnapshot.matches(clientSnapshot);

    if (!compatible) {
      logSnapshotDiff(player, clientSnapshot, backendSnapshot);
    }

    if (fastTrackable && compatible && clientPlay != null) {
      LOGGER.info("Fast transition for {} to {}: switching in PLAY (compatible registries)",
          player.getUsername(), serverConn.getServerInfo().getName());
      fastSwitch(player);
    } else {
      LOGGER.info("Fast transition for {} to {}: falling back to reconfiguration "
              + "(fastTrackable={}, compatible={}, clientInPlay={})",
          player.getUsername(), serverConn.getServerInfo().getName(),
          fastTrackable, compatible, clientPlay != null);
      fallbackReconfigure(clientPlay);
    }
  }

  /**
   * Logs, line by line, how the client's held configuration (from its current backend) differs from
   * the target backend's configuration. Ordering matters for the fingerprint, so entries are compared
   * position-by-position. Set {@code -Dvelocityctd.fasttransition.dumpDir=<dir>} to also dump the raw
   * payloads for byte-level diffing.
   */
  private void logSnapshotDiff(ConnectedPlayer player, @Nullable ConfigStateSnapshot clientSnapshot,
                               ConfigStateSnapshot backendSnapshot) {
    if (clientSnapshot == null) {
      LOGGER.warn("Fast-transition diff for {}: client has no config snapshot", player.getUsername());
      return;
    }
    List<String> client = clientSnapshot.entries();
    List<String> backend = backendSnapshot.entries();
    LOGGER.info("Fast-transition config mismatch for {} -> {}: client(fp={}, {} entries) vs backend(fp={}, {} entries)",
        player.getUsername(), serverConn.getServerInfo().getName(),
        clientSnapshot.fingerprintHex(), client.size(),
        backendSnapshot.fingerprintHex(), backend.size());
    int max = Math.max(client.size(), backend.size());
    for (int i = 0; i < max; i++) {
      String c = i < client.size() ? client.get(i) : "<none>";
      String b = i < backend.size() ? backend.get(i) : "<none>";
      if (!c.equals(b)) {
        LOGGER.info("  [{}] client={} | backend={}", i, c, b);
      }
    }
  }

  /**
   * Compatible path: discard the buffered config, advance the backend to PLAY, and let its
   * {@code JoinGame} drive an in-PLAY server switch with no client reconfiguration.
   */
  private void fastSwitch(ConnectedPlayer player) {
    releaseBuffered();

    MinecraftConnection smc = serverConn.ensureConnected();
    smc.write(FinishedUpdatePacket.INSTANCE);
    smc.setActiveSessionHandler(StateRegistry.PLAY,
        new TransitionSessionHandler(server, serverConn, resultFuture));

    // The client stays in PLAY and never re-sends its brand, so forward the stored brand to the new
    // backend ourselves; normal reconfiguration relays it via the client's own brand message.
    String brand = player.getClientBrand();
    if (brand != null) {
      ByteBuf brandData = Unpooled.buffer();
      ProtocolUtils.writeString(brandData, brand);
      smc.write(new PluginMessagePacket("minecraft:brand", brandData));
    }

    if (player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21)) {
      String target = serverConn.getServerInfo().getName();
      player.setServerLinks(server.getConfiguration().getServerLinksFor(target));
    }
  }

  /**
   * Fallback path: reproduce the standard reconfiguration by handing the connection to a fresh
   * {@link ConfigSessionHandler}, taking the client through CONFIG, and replaying the buffered
   * packets so the backend's config reaches the client exactly as it normally would.
   */
  private void fallbackReconfigure(@Nullable ClientPlaySessionHandler clientPlay) {
    MinecraftConnection smc = serverConn.ensureConnected();

    if (clientPlay == null) {
      // Should not happen: the fast handler is only installed for in-PLAY switches.
      LOGGER.error("Cannot fall back to reconfiguration for {}: client is not in PLAY",
          serverConn.getPlayer().getUsername());
      releaseBuffered();
      serverConn.getPlayer().disconnect(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
      resultFuture.complete(ConnectionRequestResults.forDisconnect(
          ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR, serverConn.getServer()));
      return;
    }

    ConfigSessionHandler normal = new ConfigSessionHandler(server, serverConn, resultFuture);
    smc.setActiveSessionHandler(StateRegistry.CONFIG, normal);
    smc.setAutoReading(false);

    // doSwitch() moves the client into CONFIG; once it resolves the client can receive the
    // backend's buffered configuration.
    clientPlay.doSwitch().thenRunAsync(() -> {
      // The backend already got its known-packs answer, so drop the client's response.
      serverConn.getPlayer().setDropKnownPacksResponseToBackend(true);
      for (MinecraftPacket packet : buffered) {
        replay(normal, packet);
      }
      buffered.clear();
      replay(normal, FinishedUpdatePacket.INSTANCE);
      smc.setAutoReading(true);
    }, smc.eventLoop()).exceptionally(ex -> {
      LOGGER.error("Error falling back to reconfiguration for {}", serverConn.getPlayer().getUsername(), ex);
      releaseBuffered();
      return null;
    });
  }

  private void replay(MinecraftSessionHandler handler, MinecraftPacket packet) {
    try {
      if (!packet.handle(handler)) {
        handler.handleGeneric(packet);
      }
    } finally {
      ReferenceCountUtil.release(packet);
    }
  }

  private void releaseBuffered() {
    for (MinecraftPacket packet : buffered) {
      ReferenceCountUtil.release(packet);
    }
    buffered.clear();
  }

  @Override
  public void disconnected() {
    releaseBuffered();
    resultFuture.complete(ConnectionRequestResults.forDisconnect(
        ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR, serverConn.getServer()));
  }

  @Override
  public void writabilityChanged() {
    // Mirror ConfigSessionHandler: relay backend writability to the client's read side.
    MinecraftConnection smc = serverConn.getConnection();
    if (smc != null) {
      serverConn.getPlayer().getConnection().setAutoReading(smc.getChannel().isWritable());
    }
  }
}
