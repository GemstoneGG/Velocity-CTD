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

package com.velocitypowered.proxy.protocol.packet.config;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a packet sent from the server to the client, containing server-related links.
 * This packet carries a list of links (e.g., URLs or other resources) associated with the server.
 */
public class ClientboundServerLinksPacket implements MinecraftPacket {

  /**
   * The list of server links provided in the packet.
   */
  private List<ServerLink> serverLinks;

  /**
   * Constructs an empty {@code ClientboundServerLinksPacket}, typically used for decoding.
   */
  public ClientboundServerLinksPacket() {
  }

  /**
   * Constructs a {@code ClientboundServerLinksPacket} with a provided list of links.
   *
   * @param serverLinks the list of links to send to the client
   */
  public ClientboundServerLinksPacket(final List<ServerLink> serverLinks) {
    this.serverLinks = serverLinks;
  }

  /**
   * Decodes this server links packet from the provided {@link ByteBuf}.
   *
   * <p>This method reads the list of server links, each consisting of an ID or display name
   * and an associated URL, and populates the internal list.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    int linksCount = ProtocolUtils.readVarInt(buf);

    this.serverLinks = new ArrayList<>(linksCount);
    for (int i = 0; i < linksCount; i++) {
      serverLinks.add(ServerLink.read(buf, version));
    }
  }

  /**
   * Encodes this server links packet into the provided {@link ByteBuf}.
   *
   * <p>This method writes each link in the internal list, serializing the ID or display name
   * and URL for each entry.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param protocolVersion the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, serverLinks.size());

    for (ServerLink serverLink : serverLinks) {
      serverLink.write(buf);
    }
  }

  /**
   * Handles this server links packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates handling to {@code handler.handle(this)} to process or display the links
   * on the client side.</p>
   *
   * @param handler the session handler responsible for handling this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Returns the list of {@link ServerLink} entries contained in this packet.
   *
   * @return the list of links
   */
  public List<ServerLink> getServerLinks() {
    return serverLinks;
  }

  /**
   * Represents a link to a server with an ID, display name, and URL.
   *
   * <p>This record holds the server's identification number, a display name
   * encapsulated in a {@code ComponentHolder}, and the server's URL as a string.</p>
   *
   * @param id the unique identifier for the server
   * @param displayName the display name of the server, represented by a {@code ComponentHolder}
   * @param url the URL of the server
   */
  public record ServerLink(int id, ComponentHolder displayName, String url) {

    private static ServerLink read(final ByteBuf buf, final ProtocolVersion version) {
      if (buf.readBoolean()) {
        return new ServerLink(ProtocolUtils.readVarInt(buf), null, ProtocolUtils.readString(buf));
      } else {
        return new ServerLink(-1, ComponentHolder.read(buf, version), ProtocolUtils.readString(buf));
      }
    }

    private void write(final ByteBuf buf) {
      if (id >= 0) {
        buf.writeBoolean(true);
        ProtocolUtils.writeVarInt(buf, id);
      } else {
        buf.writeBoolean(false);
        displayName.write(buf);
      }

      ProtocolUtils.writeString(buf, url);
    }
  }
}
