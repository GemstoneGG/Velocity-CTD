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

import static com.velocitypowered.api.event.query.ProxyQueryEvent.QueryType.BASIC;
import static com.velocitypowered.api.event.query.ProxyQueryEvent.QueryType.FULL;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableSet;
import com.velocitypowered.api.event.query.ProxyQueryEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.QueryResponse;
import com.velocitypowered.proxy.VelocityServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.logging.log4j.LogManager;

/**
 * Implements the GameSpy protocol for Velocity.
 */
public class GameSpyQueryHandler extends SimpleChannelInboundHandler<DatagramPacket> {

  /**
   * Magic byte prefix that must be present for a valid GS4 packet (first).
   */
  private static final short QUERY_MAGIC_FIRST = 0xFE;

  /**
   * Magic byte prefix that must be present for a valid GS4 packet (second).
   */
  private static final short QUERY_MAGIC_SECOND = 0xFD;

  /**
   * Packet type for a handshake request.
   */
  private static final byte QUERY_TYPE_HANDSHAKE = 0x09;

  /**
   * Packet type for a stat request (basic or full).
   */
  private static final byte QUERY_TYPE_STAT = 0x00;

  /**
   * Padding used at the start of a full stat response key-value section.
   */
  private static final byte[] QUERY_RESPONSE_FULL_PADDING = {
      0x73, 0x70, 0x6C, 0x69, 0x74, 0x6E, 0x75, 0x6D, 0x00, (byte) 0x80, 0x00
  };

  /**
   * Padding used to denote the beginning of the player list section in full stat responses.
   */
  private static final byte[] QUERY_RESPONSE_FULL_PADDING2 = {
      0x01, 0x70, 0x6C, 0x61, 0x79, 0x65, 0x72, 0x5F, 0x00, 0x00
  };

  /**
   * Keys included in basic stat responses.
   */
  private static final ImmutableSet<String> QUERY_BASIC_RESPONSE_CONTENTS = ImmutableSet.of(
      "hostname",
      "gametype",
      "map",
      "numplayers",
      "maxplayers",
      "hostport",
      "hostip"
  );

  /**
   * A cache of challenge tokens issued to clients during the GS4 handshake phase,
   * keyed by their {@link InetAddress}. Entries expire after 30 seconds.
   */
  private final Cache<InetAddress, Integer> sessions = Caffeine.newBuilder()
      .expireAfterWrite(30, TimeUnit.SECONDS)
      .build();

  /**
   * A secure random number generator used for generating challenge tokens
   * in the GS4 handshake protocol.
   */
  private final SecureRandom random;

  /**
   * The Velocity server instance used for accessing configuration,
   * plugin and player data, and event management.
   */
  private final VelocityServer server;

  /**
   * Constructs a new {@link GameSpyQueryHandler} instance responsible for
   * handling GS4 query packets.
   *
   * @param server the {@link VelocityServer} instance to associate with this handler
   */
  public GameSpyQueryHandler(final VelocityServer server) {
    this.server = server;
    this.random = new SecureRandom();
  }

  private QueryResponse createInitialResponse() {

    final int online;
    if (server.getMultiProxyHandler().isRedisEnabled()) {
      online = server.getMultiProxyHandler().getTotalPlayerCount();
    } else {
      online = server.getPlayerCount();
    }

    return QueryResponse.builder()
        .hostname(PlainTextComponentSerializer.plainText().serialize(server.getConfiguration().getMotd()))
        .gameVersion(ProtocolVersion.SUPPORTED_VERSION_STRING)
        .map(server.getConfiguration().getQueryMap())
        .currentPlayers(online)
        .maxPlayers(server.getConfiguration().getShowMaxPlayers())
        .proxyPort(server.getConfiguration().getBind().getPort())
        .proxyHost(server.getConfiguration().getBind().getHostString())
        .players(server.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toList()))
        .proxyVersion("Velocity")
        .plugins(server.getConfiguration().shouldQueryShowPlugins() ? getRealPluginInformation() : Collections.emptyList())
        .build();
  }

  /**
   * Handles an incoming GS4 (GameSpy4) query {@link DatagramPacket}.
   *
   * <p>This method distinguishes between handshake (0x09) and stat (0x00) requests,
   * validates the packet header and session, and constructs appropriate query responses
   * using the Velocity server configuration and plugins.</p>
   *
   * <ul>
   *   <li>If the packet is a handshake, a challenge token is generated and sent back to the client.</li>
   *   <li>If the packet is a stat request, the challenge token is verified and a query response is built.</li>
   *   <li>If the packet type is unknown or invalid, the request is ignored silently.</li>
   * </ul>
   *
   * @param ctx the Netty channel context
   * @param msg the incoming datagram query packet
   */
  @Override
  protected void channelRead0(final ChannelHandlerContext ctx, final DatagramPacket msg) {
    ByteBuf queryMessage = msg.content();
    InetAddress senderAddress = msg.sender().getAddress();

    // Verify query packet magic
    if (queryMessage.readUnsignedByte() != QUERY_MAGIC_FIRST
        || queryMessage.readUnsignedByte() != QUERY_MAGIC_SECOND) {
      return;
    }

    // Read packet header
    short type = queryMessage.readUnsignedByte();
    int sessionId = queryMessage.readInt();

    switch (type) {
      case QUERY_TYPE_HANDSHAKE -> {
        // Generate a new challenge token and put it into the session cache
        int challengeToken = random.nextInt();
        sessions.put(senderAddress, challengeToken);

        // Respond with challenge token
        ByteBuf queryResponse = ctx.alloc().buffer();
        queryResponse.writeByte(QUERY_TYPE_HANDSHAKE);
        queryResponse.writeInt(sessionId);
        writeString(queryResponse, Integer.toString(challengeToken));

        DatagramPacket responsePacket = new DatagramPacket(queryResponse, msg.sender());
        ctx.writeAndFlush(responsePacket, ctx.voidPromise());
      }
      case QUERY_TYPE_STAT -> {
        // Check if a query was done with session previously generated using a handshake packet
        int challengeToken = queryMessage.readInt();
        Integer session = sessions.getIfPresent(senderAddress);
        if (session == null || session != challengeToken) {
          return;
        }

        // Check which query response client expects
        if (queryMessage.readableBytes() != 0 && queryMessage.readableBytes() != 4) {
          return;
        }

        // Build initial query response
        QueryResponse response = createInitialResponse();
        boolean isBasic = !queryMessage.isReadable();

        // Call event and write response
        server.getEventManager()
            .fire(new ProxyQueryEvent(isBasic ? BASIC : FULL, senderAddress, response))
            .thenAcceptAsync((event) -> {
              // Packet header
              ByteBuf queryResponse = ctx.alloc().buffer();
              queryResponse.writeByte(QUERY_TYPE_STAT);
              queryResponse.writeInt(sessionId);

              // Start writing the response
              ResponseWriter responseWriter = new ResponseWriter(queryResponse, isBasic);
              responseWriter.write("hostname", event.getResponse().getHostname());
              responseWriter.write("gametype", "SMP");

              responseWriter.write("game_id", "MINECRAFT");
              responseWriter.write("version", event.getResponse().getGameVersion());
              responseWriter.writePlugins(event.getResponse().getProxyVersion(),
                  event.getResponse().getPlugins());

              responseWriter.write("map", event.getResponse().getMap());
              responseWriter.write("numplayers", event.getResponse().getCurrentPlayers());
              responseWriter.write("maxplayers", event.getResponse().getMaxPlayers());
              responseWriter.write("hostport", event.getResponse().getProxyPort());
              responseWriter.write("hostip", event.getResponse().getProxyHost());

              if (!responseWriter.isBasic) {
                responseWriter.writePlayers(event.getResponse().getPlayers());
              }

              // Send the response
              DatagramPacket responsePacket = new DatagramPacket(queryResponse, msg.sender());
              ctx.writeAndFlush(responsePacket, ctx.voidPromise());
            }, ctx.channel().eventLoop())
            .exceptionally((ex) -> {
              LogManager.getLogger(getClass()).error(
                  "Exception while writing GS4 response for query from {}", senderAddress, ex);
              return null;
            });
      }
      default -> {
          // Invalid query type - just don't respond
      }
    }
  }

  private static void writeString(final ByteBuf buf, final String string) {
    buf.writeCharSequence(string, StandardCharsets.ISO_8859_1);
    buf.writeByte(0x00);
  }

  private List<QueryResponse.PluginInformation> getRealPluginInformation() {
    List<QueryResponse.PluginInformation> result = new ArrayList<>();
    for (PluginContainer plugin : server.getPluginManager().getPlugins()) {
      PluginDescription description = plugin.getDescription();
      result.add(QueryResponse.PluginInformation.of(description.getName()
          .orElse(description.getId()), description.getVersion().orElse(null)));
    }

    return result;
  }

  private static class ResponseWriter {

    /**
     * The {@link ByteBuf} into which the response is written.
     */
    private final ByteBuf buf;

    /**
     * Whether this writer is constructing a basic response.
     */
    private final boolean isBasic;

    ResponseWriter(final ByteBuf buf, final boolean isBasic) {
      this.buf = buf;
      this.isBasic = isBasic;

      if (!isBasic) {
        buf.writeBytes(QUERY_RESPONSE_FULL_PADDING);
      }
    }

    // Writes k/v to stat packet body if this writer is initialized
    // for full stat response. Otherwise, this follows
    // GS4QueryHandler#QUERY_BASIC_RESPONSE_CONTENTS to decide what
    // to write into the packet body
    void write(final String key, final Object value) {
      if (isBasic) {
        // Basic contains only specific-set of data
        if (!QUERY_BASIC_RESPONSE_CONTENTS.contains(key)) {
          return;
        }

        // Special case hostport
        if (key.equals("hostport")) {
          buf.writeShortLE((Integer) value);
        } else {
          writeString(buf, value.toString());
        }
      } else {
        writeString(buf, key);
        writeString(buf, value.toString());
      }
    }

    // "Ends" packet k/v body writing and writes a stat player list to
    // the packet if this writer is initialized for full stat response
    void writePlayers(final Collection<String> players) {
      if (isBasic) {
        return;
      }

      // Ends the full stat key-value body with \0
      buf.writeByte(0x00);

      buf.writeBytes(QUERY_RESPONSE_FULL_PADDING2);
      players.forEach(player -> writeString(buf, player));
      buf.writeByte(0x00);
    }

    void writePlugins(final String serverVersion, final Collection<QueryResponse.PluginInformation> plugins) {
      if (isBasic) {
        return;
      }

      StringBuilder pluginsString = new StringBuilder();
      pluginsString.append(serverVersion).append(':').append(' ');
      Iterator<QueryResponse.PluginInformation> iterator = plugins.iterator();
      while (iterator.hasNext()) {
        QueryResponse.PluginInformation info = iterator.next();
        pluginsString.append(info.getName());
        Optional<String> version = info.getVersion();
        version.ifPresent(s -> pluginsString.append(' ').append(s));
        if (iterator.hasNext()) {
          pluginsString.append(';').append(' ');
        }
      }

      write("plugins", pluginsString.toString());
    }
  }
}
