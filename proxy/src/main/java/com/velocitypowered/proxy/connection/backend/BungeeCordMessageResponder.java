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

package com.velocitypowered.proxy.connection.backend;

import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.UuidUtils;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.util.ByteBufDataInput;
import com.velocitypowered.proxy.protocol.util.ByteBufDataOutput;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Handles messages coming from servers trying to communicate with the BungeeCord plugin
 * messaging channel interface.
 */
@SuppressFBWarnings(
    value = "OS_OPEN_STREAM",
    justification = "Most methods in this class open "
      + "instances of ByteBufDataOutput backed by heap-allocated ByteBufs. Closing them does "
      + "nothing."
)
public class BungeeCordMessageResponder {

  private static final MinecraftChannelIdentifier MODERN_CHANNEL = MinecraftChannelIdentifier
      .create("bungeecord", "main");

  private static final LegacyChannelIdentifier LEGACY_CHANNEL = new LegacyChannelIdentifier("BungeeCord");

  private final VelocityServer proxy;

  private final ConnectedPlayer player;

  BungeeCordMessageResponder(VelocityServer proxy, ConnectedPlayer player) {
    this.proxy = proxy;
    this.player = player;
  }

  public static boolean isBungeeCordMessage(PluginMessagePacket message) {
    return MODERN_CHANNEL.getId().equals(message.getChannel()) || LEGACY_CHANNEL.getId().equals(message.getChannel());
  }

  private void processConnect(ByteBufDataInput in, boolean queue) {
    String serverName = in.readUTF();

    if (player.getPermissionValue("velocity.command.server." + serverName) == Tristate.FALSE) {
      player.sendMessage(Component.translatable("velocity.command.server-does-not-exist")
          .arguments(Component.text(serverName)));
      return;
    }

    proxy.getServer(serverName).ifPresent(server -> {
      if (queue && proxy.isQueueEnabled()) {
        if (this.proxy.getConfiguration().getQueue().getNoQueueServers().contains(server.getServerInfo().getName())) {
          player.createConnectionRequest(server).connectWithIndication();
          return;
        }

        if (player.hasPermission("velocity.queue.bypass")) {
          player.createConnectionRequest(server).connectWithIndication();
        } else {
          proxy.getQueueManager().queue(player, server);
        }
      } else {
        player.createConnectionRequest(server).fireAndForget();
      }
    });
  }

  private void processConnectOther(ByteBufDataInput in, boolean queue) {
    String playerName = in.readUTF();
    String serverName = in.readUTF();

    Optional<ConnectedPlayer> referencedPlayer = proxy.getPlayer(playerName);
    Optional<VelocityRegisteredServer> referencedServer = proxy.getServer(serverName);
    if (referencedPlayer.isPresent() && referencedServer.isPresent()) {
      if (referencedPlayer.get().getPermissionValue("velocity.command.server." + serverName) == Tristate.FALSE) {
        referencedPlayer.get().sendMessage(Component.translatable("velocity.command.server-does-not-exist")
            .arguments(Component.text(serverName)));
        return;
      }

      if (queue && proxy.isQueueEnabled()) {
        if (this.proxy.getConfiguration().getQueue().getNoQueueServers().contains(referencedServer.get().getServerInfo().getName())) {
          referencedPlayer.get().createConnectionRequest(referencedServer.get()).connectWithIndication();
          return;
        }

        if (!referencedPlayer.get().hasPermission("velocity.queue.bypass")) {
          proxy.getQueueManager().queue(referencedPlayer.get(), referencedServer.get());
        } else {
          referencedPlayer.get().createConnectionRequest(referencedServer.get()).fireAndForget();
        }
      } else {
        referencedPlayer.get().createConnectionRequest(referencedServer.get()).fireAndForget();
      }
    }
  }

  private void processIp(ByteBufDataInput ignoredIn) {
    ByteBuf buf = Unpooled.buffer();
    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
      out.writeUTF("IP");
      out.writeUTF(player.getRemoteAddress().getHostString());
      out.writeInt(player.getRemoteAddress().getPort());
    }

    sendResponseOnConnection(buf);
  }

  private void processPlayerCount(ByteBufDataInput in) {
    ByteBuf buf = Unpooled.buffer();
    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {

      String target = in.readUTF();
      if (target.equals("ALL")) {
        out.writeUTF("PlayerCount");
        out.writeUTF("ALL");
        out.writeInt(proxy.getClusterPlayerService().getTotalPlayerCount());
      } else {
        proxy.getServer(target).ifPresent(rs -> {
          out.writeUTF("PlayerCount");
          out.writeUTF(rs.getServerInfo().getName());
          out.writeInt(proxy.getClusterPlayerService()
              .getPlayersOnServerCount(rs.getServerInfo().getName()));
        });
      }
    }

    if (buf.isReadable()) {
      sendResponseOnConnection(buf);
    } else {
      buf.release();
    }
  }

  private void processPing(ByteBufDataInput in) {
    UUID playerUuid = UUID.fromString(in.readUTF());

    ByteBuf buf = Unpooled.buffer();

    ConnectedPlayer player = this.proxy.getPlayer(playerUuid).orElse(null);
    if (player == null) {
      return;
    }

    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
      out.writeUTF("Ping");
      out.writeUTF(player.getUniqueId().toString());
      out.writeInt((int) player.getPing());
    }

    if (buf.isReadable()) {
      sendResponseOnConnection(buf);
    } else {
      buf.release();
    }
  }

  private void queuedServer(ByteBufDataInput in) {
    UUID playerUuid = UUID.fromString(in.readUTF());

    ByteBuf buf = Unpooled.buffer();

    String queuedServer = null;

    if (proxy.isQueueEnabled()) {
      for (VelocityQueue<?> queue : proxy.getQueueManager().getQueues()) {
        if (queue.contains(playerUuid)) {
          queuedServer = queue.getName();
          break;
        }
      }
    }

    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
      out.writeUTF("QueuedServer");
      out.writeUTF(playerUuid.toString());
      out.writeUTF(Objects.requireNonNullElse(queuedServer, "N/A"));
    }

    if (buf.isReadable()) {
      sendResponseOnConnection(buf);
    } else {
      buf.release();
    }
  }

  private void queuedPosition(ByteBufDataInput in) {
    UUID playerUuid = UUID.fromString(in.readUTF());

    ByteBuf buf = Unpooled.buffer();

    Integer position = null;
    if (proxy.isQueueEnabled()) {
      for (VelocityQueue<?> queue : proxy.getQueueManager().getQueues()) {
        if (queue.contains(playerUuid)) {
          position = queue.getPosition(playerUuid).orElse(null);
          if (position != null) {
            break;
          }
        }
      }
    }

    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
      out.writeUTF("QueuedPosition");
      out.writeUTF(playerUuid.toString());
      out.writeInt(position != null ? position : -1);
    }

    if (buf.isReadable()) {
      sendResponseOnConnection(buf);
    } else {
      buf.release();
    }
  }

  private void queuedMaxPosition(ByteBufDataInput in) {
    UUID playerUuid = UUID.fromString(in.readUTF());

    ByteBuf buf = Unpooled.buffer();

    int position = -1;

    if (proxy.isQueueEnabled()) {
      for (VelocityQueue<?> queue : proxy.getQueueManager().getQueues()) {
        if (queue.contains(playerUuid)) {
          position = queue.size();
          break;
        }
      }
    }

    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
      out.writeUTF("MaxQueuedPosition");
      out.writeUTF(playerUuid.toString());
      out.writeInt(position);
    }

    if (buf.isReadable()) {
      sendResponseOnConnection(buf);
    } else {
      buf.release();
    }
  }

  private void queuedPaused(ByteBufDataInput in) {
    UUID playerUuid = UUID.fromString(in.readUTF());

    ByteBuf buf = Unpooled.buffer();

    boolean paused = false;

    if (proxy.isQueueEnabled()) {
      for (VelocityQueue<?> queue : proxy.getQueueManager().getQueues()) {
        if (queue.contains(playerUuid)) {
          paused = true;
          break;
        }
      }
    }

    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
      out.writeUTF("QueuedPausedChannel");
      out.writeUTF(playerUuid.toString());
      out.writeBoolean(paused);
    }

    if (buf.isReadable()) {
      sendResponseOnConnection(buf);
    } else {
      buf.release();
    }
  }

  private void processPlayerList(ByteBufDataInput in) {
    ByteBuf buf = Unpooled.buffer();

    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {

      String target = in.readUTF();
      if (target.equals("ALL")) {
        out.writeUTF("PlayerList");
        out.writeUTF("ALL");

        StringJoiner joiner = new StringJoiner(", ");
        for (ConnectedPlayer online : proxy.getOnlinePlayers()) {
          joiner.add(online.getUsername());
        }

        out.writeUTF(joiner.toString());
      } else {
        proxy.getServer(target).ifPresent(info -> {
          out.writeUTF("PlayerList");
          out.writeUTF(info.getServerInfo().getName());

          StringJoiner joiner = new StringJoiner(", ");
          for (ConnectedPlayer online : info.getPlayersConnected()) {
            joiner.add(online.getUsername());
          }

          out.writeUTF(joiner.toString());
        });
      }
    }

    if (buf.isReadable()) {
      sendResponseOnConnection(buf);
    } else {
      buf.release();
    }
  }

  private void processGetServers() {
    StringJoiner joiner = new StringJoiner(", ");
    for (VelocityRegisteredServer server : proxy.getAllServers()) {
      joiner.add(server.getServerInfo().getName());
    }

    ByteBuf buf = Unpooled.buffer();

    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
      out.writeUTF("GetServers");
      out.writeUTF(joiner.toString());
    }

    sendResponseOnConnection(buf);
  }

  private void processMessage(ByteBufDataInput in) {
    processMessage0(in, LegacyComponentSerializer.legacySection());
  }

  private void processMessageRaw(ByteBufDataInput in) {
    processMessage0(in, GsonComponentSerializer.gson());
  }

  private void processMessage0(ByteBufDataInput in,
                               ComponentSerializer<Component, ?, String> serializer) {
    String target = in.readUTF();
    String message = in.readUTF();

    Component messageComponent = serializer.deserialize(message);
    if (target.equals("ALL")) {
      proxy.sendMessage(messageComponent);
    } else {
      proxy.getPlayer(target).ifPresent(player -> player.sendMessage(messageComponent));
    }
  }

  private void processGetServer() {
    ByteBuf buf = Unpooled.buffer();

    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
      out.writeUTF("GetServer");
      out.writeUTF(player.ensureAndGetCurrentServer().getServerInfo().getName());
    }

    sendResponseOnConnection(buf);
  }

  private void processUuid() {
    ByteBuf buf = Unpooled.buffer();

    try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
      out.writeUTF("UUID");
      out.writeUTF(UuidUtils.toUndashed(player.getUniqueId()));
    }

    sendResponseOnConnection(buf);
  }

  private void processUuidOther(ByteBufDataInput in) {
    proxy.getPlayer(in.readUTF()).ifPresent(player -> {
      ByteBuf buf = Unpooled.buffer();
      try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
        out.writeUTF("UUIDOther");
        out.writeUTF(player.getUsername());
        out.writeUTF(UuidUtils.toUndashed(player.getUniqueId()));
      }

      sendResponseOnConnection(buf);
    });
  }

  private void processIpOther(ByteBufDataInput in) {
    proxy.getPlayer(in.readUTF()).ifPresent(player -> {
      ByteBuf buf = Unpooled.buffer();
      try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
        out.writeUTF("IPOther");
        out.writeUTF(player.getUsername());
        out.writeUTF(player.getRemoteAddress().getHostString());
        out.writeInt(player.getRemoteAddress().getPort());
      }

      sendResponseOnConnection(buf);
    });
  }

  private void processServerIp(ByteBufDataInput in) {
    proxy.getServer(in.readUTF()).ifPresent(info -> {
      ByteBuf buf = Unpooled.buffer();
      try (ByteBufDataOutput out = new ByteBufDataOutput(buf)) {
        out.writeUTF("ServerIP");
        out.writeUTF(info.getServerInfo().getName());
        out.writeUTF(info.getServerInfo().getAddress().getHostString());
        out.writeShort(info.getServerInfo().getAddress().getPort());
      }

      sendResponseOnConnection(buf);
    });
  }

  private void processKick(ByteBufDataInput in) {
    proxy.getPlayer(in.readUTF()).ifPresent(player -> {
      String kickReason = in.readUTF();
      player.disconnect(LegacyComponentSerializer.legacySection().deserialize(kickReason));
    });
  }

  private void processKickRaw(ByteBufDataInput in) {
    proxy.getPlayer(in.readUTF()).ifPresent(player -> {
      String kickReason = in.readUTF();
      player.disconnect(GsonComponentSerializer.gson().deserialize(kickReason));
    });
  }

  private void processForwardToPlayer(ByteBufDataInput in) {
    Optional<ConnectedPlayer> player = proxy.getPlayer(in.readUTF());
    if (player.isPresent()) {
      ByteBuf toForward = in.unwrap().copy();
      sendServerResponse(player.get(), toForward);
    }
  }

  private void processForwardToServer(ByteBufDataInput in) {
    String target = in.readUTF();
    ByteBuf toForward = in.unwrap().copy();
    ServerInfo currentUserServer = player
        .getCurrentServer()
        .map(VelocityServerConnection::getServerInfo)
        .orElse(null);
    if (target.equals("ALL") || target.equals("ONLINE")) {
      try {
        for (VelocityRegisteredServer rs : proxy.getAllServers()) {
          if (!rs.getServerInfo().equals(currentUserServer)) {
            rs.sendPluginMessage(LEGACY_CHANNEL, toForward.retainedSlice());
          }
        }
      } finally {
        toForward.release();
      }
    } else {
      Optional<VelocityRegisteredServer> server = proxy.getServer(target);
      if (server.isPresent()) {
        server.get().sendPluginMessage(LEGACY_CHANNEL, toForward);
      } else {
        toForward.release();
      }
    }
  }

  private void processGetPlayerServer(ByteBufDataInput in) {
    proxy.getPlayer(in.readUTF()).ifPresent(player -> player.getCurrentServer().ifPresent(server -> {
      ByteBuf buf = Unpooled.buffer();
      ByteBufDataOutput out = new ByteBufDataOutput(buf);
      out.writeUTF("GetPlayerServer");
      out.writeUTF(player.getUsername());
      out.writeUTF(server.getServerInfo().getName());
      sendResponseOnConnection(buf);
    }));
  }

  static ChannelIdentifier getBungeeCordChannel(ProtocolVersion version) {
    return version.noLessThan(ProtocolVersion.MINECRAFT_1_13) ? MODERN_CHANNEL : LEGACY_CHANNEL;
  }

  // Note: this method will always release the buffer!
  private void sendResponseOnConnection(ByteBuf buf) {
    sendServerResponse(this.player, buf);
  }

  // Note: this method will always release the buffer!
  private static void sendServerResponse(ConnectedPlayer player, ByteBuf buf) {
    MinecraftConnection serverConnection = player.ensureAndGetCurrentServer().ensureConnected();
    ChannelIdentifier chan = getBungeeCordChannel(serverConnection.getProtocolVersion());
    PluginMessagePacket msg = new PluginMessagePacket(chan.getId(), buf);
    serverConnection.write(msg);
  }

  final boolean process(PluginMessagePacket message) {
    if (!proxy.getConfiguration().isBungeePluginChannelEnabled()) {
      return false;
    }

    if (!isBungeeCordMessage(message)) {
      return false;
    }

    ByteBufDataInput in = new ByteBufDataInput(message.content());
    String subChannel = in.readUTF();
    switch (subChannel) {
      case "GetPlayerServer" -> this.processGetPlayerServer(in);
      case "ForwardToPlayer" -> this.processForwardToPlayer(in);
      case "Forward" -> this.processForwardToServer(in);
      case "ConnectDirect" -> this.processConnect(in, false);
      case "ConnectQueue" -> this.processConnect(in, true);
      case "Connect" -> this.processConnect(in, proxy.getConfiguration().getQueue().shouldOverrideBungeeMessaging());
      case "ConnectOtherDirect" -> this.processConnectOther(in, false);
      case "ConnectOtherQueue" -> this.processConnectOther(in, true);
      case "ConnectOther" -> this.processConnectOther(in, proxy.getConfiguration().getQueue().shouldOverrideBungeeMessaging());
      case "IP" -> this.processIp(in);
      case "PlayerCount" -> this.processPlayerCount(in);
      case "PlayerList" -> this.processPlayerList(in);
      case "GetServers" -> this.processGetServers();
      case "Message" -> this.processMessage(in);
      case "MessageRaw" -> this.processMessageRaw(in);
      case "GetServer" -> this.processGetServer();
      case "UUID" -> this.processUuid();
      case "UUIDOther" -> this.processUuidOther(in);
      case "IPOther" -> this.processIpOther(in);
      case "ServerIP" -> this.processServerIp(in);
      case "KickPlayer" -> this.processKick(in);
      case "KickPlayerRaw" -> this.processKickRaw(in);
      case "Ping" -> this.processPing(in);
      case "QueuedServer" -> this.queuedServer(in);
      case "QueuedPosition" -> this.queuedPosition(in);
      case "MaxQueuedPosition" -> this.queuedMaxPosition(in);
      case "QueuedPausedChannel" -> this.queuedPaused(in);
      default -> {
        // Do nothing, unknown command
      }
    }

    return true;
  }
}
