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

package com.velocitypowered.proxy.protocol.packet;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;

/**
 * Represents a packet that contains both the header and footer for the player list screen (tab list) in Minecraft.
 * This packet allows the server to set or update the header and footer text that is displayed on the client's tab list.
 */
public class HeaderAndFooterPacket implements MinecraftPacket {

  /**
   * The header component to be displayed at the top of the player's tab list.
   */
  private final ComponentHolder header;

  /**
   * The footer component to be displayed at the bottom of the player's tab list.
   */
  private final ComponentHolder footer;

  /**
   * Constructs a {@code HeaderAndFooterPacket} for decoding purposes.
   *
   * <p>This constructor is not implemented and will always throw an exception.</p>
   *
   * @throws UnsupportedOperationException always thrown since decoding is not supported
   */
  public HeaderAndFooterPacket() {
    throw new UnsupportedOperationException("Decode is not implemented");
  }

  /**
   * Constructs a {@code HeaderAndFooterPacket} with the given header and footer components.
   *
   * @param header the component to display in the tab list header
   * @param footer the component to display in the tab list footer
   */
  public HeaderAndFooterPacket(final ComponentHolder header, final ComponentHolder footer) {
    this.header = Preconditions.checkNotNull(header, "header");
    this.footer = Preconditions.checkNotNull(footer, "footer");
  }

  /**
   * Returns the header component of this packet.
   *
   * @return the header component
   */
  public ComponentHolder getHeader() {
    return header;
  }

  /**
   * Returns the footer component of this packet.
   *
   * @return the footer component
   */
  public ComponentHolder getFooter() {
    return footer;
  }

  /**
   * Throws an {@link UnsupportedOperationException} because decoding is not supported.
   *
   * <p>This packet is intended to be sent from the server to the client only,
   * and decoding is not implemented by design.</p>
   *
   * @param buf the buffer to read from
   * @param direction the packet direction
   * @param version the Minecraft protocol version
   * @throws UnsupportedOperationException always
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    throw new UnsupportedOperationException("Decode is not implemented");
  }

  /**
   * Encodes this header and footer packet into the given {@link ByteBuf}.
   *
   * <p>This writes both the header and footer components to the buffer using the protocol
   * version-specific serialization rules defined in {@link ComponentHolder}.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    header.write(buf);
    footer.write(buf);
  }

  /**
   * Handles this header and footer packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} to update
   * the player's tab list components.</p>
   *
   * @param handler the session handler responsible for handling this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Creates a new {@link HeaderAndFooterPacket} with the specified header and footer components.
   *
   * @param header the {@link Component} to display as the header
   * @param footer the {@link Component} to display as the footer
   * @param protocolVersion the {@link ProtocolVersion} used to serialize the components
   * @return a {@link HeaderAndFooterPacket} with the specified components
   */
  public static HeaderAndFooterPacket create(final Component header,
                                             final Component footer, final ProtocolVersion protocolVersion) {
    return new HeaderAndFooterPacket(new ComponentHolder(protocolVersion, header),
        new ComponentHolder(protocolVersion, footer));
  }

  /**
   * Creates a new {@link HeaderAndFooterPacket} with empty header and footer components.
   *
   * @param version the {@link ProtocolVersion} used to serialize the empty components
   * @return a {@link HeaderAndFooterPacket} with no header or footer
   */
  public static HeaderAndFooterPacket reset(final ProtocolVersion version) {
    ComponentHolder empty = new ComponentHolder(version, Component.empty());
    return new HeaderAndFooterPacket(empty, empty);
  }
}
