/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.util.Favicon;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the server data packet sent from the server to the client, which contains information
 * such as the server description, favicon, and secure chat enforcement status.
 */
public class ServerDataPacket implements MinecraftPacket {

  /**
   * The server description component shown in the server list.
   */
  private @Nullable ComponentHolder description;

  /**
   * The server favicon displayed in the server list.
   */
  private @Nullable Favicon favicon;

  /**
   * Whether secure chat is enforced (only present from 1.19.1 to 1.20.4).
   */
  private boolean secureChatEnforced;

  /**
   * Constructs an empty {@code ServerDataPacket}.
   *
   * <p>Fields must be manually set before encoding.</p>
   */
  public ServerDataPacket() {
  }

  /**
   * Constructs a new {@code ServerDataPacket} with the given server description, favicon, and secure chat enforcement status.
   *
   * @param description the server description (maybe null)
   * @param favicon the server favicon (maybe null)
   * @param secureChatEnforced whether secure chat is enforced (for versions 1.19.1 to 1.20.5)
   */
  public ServerDataPacket(@Nullable final ComponentHolder description, @Nullable final Favicon favicon,
                          final boolean secureChatEnforced) {
    this.description = description;
    this.favicon = favicon;
    this.secureChatEnforced = secureChatEnforced;
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_4) || buf.readBoolean()) {
      this.description = ComponentHolder.read(buf, protocolVersion);
    }

    if (buf.readBoolean()) {
      String iconBase64;
      if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_4)) {
        byte[] iconBytes = ProtocolUtils.readByteArray(buf);
        iconBase64 = "data:image/png;base64," + new String(Base64.getEncoder().encode(iconBytes), StandardCharsets.UTF_8);
      } else {
        iconBase64 = ProtocolUtils.readString(buf);
      }

      this.favicon = new Favicon(iconBase64);
    }

    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
      buf.readBoolean();
    }

    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)
            && protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      this.secureChatEnforced = buf.readBoolean();
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    boolean hasDescription = this.description != null;
    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_19_4)) {
      buf.writeBoolean(hasDescription);
    }

    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_4) || hasDescription) {
      this.description.write(buf);
    }

    boolean hasFavicon = this.favicon != null;
    buf.writeBoolean(hasFavicon);
    if (hasFavicon) {
      if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_4)) {
        String cutIconBase64 = favicon.getBase64Url().substring("data:image/png;base64,".length());
        byte[] iconBytes = Base64.getDecoder().decode(cutIconBase64.getBytes(StandardCharsets.UTF_8));
        ProtocolUtils.writeByteArray(buf, iconBytes);
      } else {
        ProtocolUtils.writeString(buf, favicon.getBase64Url());
      }
    }

    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
      buf.writeBoolean(false);
    }

    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_1)
            && protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      buf.writeBoolean(this.secureChatEnforced);
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Returns the server description component.
   *
   * @return the description component, or {@code null} if not set
   */
  public @Nullable ComponentHolder getDescription() {
    return description;
  }

  /**
   * Returns the server favicon.
   *
   * @return the {@link Favicon}, or {@code null} if not set
   */
  public @Nullable Favicon getFavicon() {
    return favicon;
  }

  /**
   * Returns whether secure chat is enforced by the server.
   *
   * @return {@code true} if secure chat is enforced, {@code false} otherwise
   */
  public boolean isSecureChatEnforced() {
    return secureChatEnforced;
  }

  /**
   * Sets whether secure chat is enforced.
   *
   * @param secureChatEnforced {@code true} to enforce secure chat, {@code false} to disable
   */
  public void setSecureChatEnforced(final boolean secureChatEnforced) {
    this.secureChatEnforced = secureChatEnforced;
  }
}
