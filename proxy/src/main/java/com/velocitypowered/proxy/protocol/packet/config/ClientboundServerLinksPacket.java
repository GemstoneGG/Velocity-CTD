/*
 * Copyright (C) 2024 Velocity Contributors
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
 * Represents a packet that is sent from the server to the client, containing server-related links.
 * This packet carries a list of links (e.g., URLs or other resources) associated with the server.
 */
public class ClientboundServerLinksPacket implements MinecraftPacket {

  private List<ServerLink> serverLinks;

  public ClientboundServerLinksPacket() {
  }

  public ClientboundServerLinksPacket(final List<ServerLink> serverLinks) {
    this.serverLinks = serverLinks;
  }

  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    int linksCount = ProtocolUtils.readVarInt(buf);

    this.serverLinks = new ArrayList<>(linksCount);
    for (int i = 0; i < linksCount; i++) {
      serverLinks.add(ServerLink.read(buf, version));
    }
  }

  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, serverLinks.size());

    for (ServerLink serverLink : serverLinks) {
      serverLink.write(buf);
    }
  }

  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

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

    /**
    * Constructs a new {@link ServerLink} instance by converting the provided
    * {@link com.velocitypowered.api.util.ServerLink} and considering the specified protocol
    * version.
    *
    * @param link the {@link com.velocitypowered.api.util.ServerLink} object containing the
    *             original link details
    * @param protocolVersion the version of the protocol being used, which may influence how the
    *                        link is handled or displayed
    */
    public ServerLink(final com.velocitypowered.api.util.ServerLink link, final ProtocolVersion protocolVersion) {
      this(link.getBuiltInType().map(Enum::ordinal).orElse(-1),
          link.getCustomLabel().map(c -> new ComponentHolder(protocolVersion, c)).orElse(null),
          link.getUrl().toString());
    }

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
