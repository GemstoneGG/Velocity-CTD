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

  public ClientboundServerLinksPacket(List<ServerLink> serverLinks) {
    this.serverLinks = serverLinks;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    int linksCount = ProtocolUtils.readVarInt(buf);

    this.serverLinks = new ArrayList<>(linksCount);
    for (int i = 0; i < linksCount; i++) {
      serverLinks.add(ServerLink.read(buf, version));
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction,
      ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, serverLinks.size());

    for (ServerLink serverLink : serverLinks) {
      serverLink.write(buf);
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  public List<ServerLink> getServerLinks() {
    return serverLinks;
  }

  /**
   * Represents a server link, which includes an identifier, a display name, and a URL.
   * This class is a record that encapsulates these three fields.
   *
   * @param id the unique identifier for the link
   * @param displayName the display name of the link, stored as a {@link ComponentHolder}
   *                    for flexible text representation
   * @param url the URL or resource link represented by this server link
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
    public ServerLink(com.velocitypowered.api.util.ServerLink link,
        ProtocolVersion protocolVersion) {
      this(link.getBuiltInType().map(Enum::ordinal).orElse(-1),
          link.getCustomLabel().map(c -> new ComponentHolder(protocolVersion, c)).orElse(null),
          link.getUrl().toString());
    }

    private static ServerLink read(ByteBuf buf, ProtocolVersion version) {
      if (buf.readBoolean()) {
        return new ServerLink(ProtocolUtils.readVarInt(buf), null, ProtocolUtils.readString(buf));
      } else {
        return new ServerLink(-1, ComponentHolder.read(buf, version),
            ProtocolUtils.readString(buf));
      }
    }

    private void write(ByteBuf buf) {
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
