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
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.util.VelocityProperties;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents the packet sent from the server to the client to indicate successful login.
 * This packet contains the player's UUID, username, and properties associated with their profile.
 */
public class ServerLoginSuccessPacket implements MinecraftPacket {

  /**
   * The UUID of the player, as provided by the login success response.
   */
  private @Nullable UUID uuid;

  /**
   * The username of the player.
   */
  private @Nullable String username;

  /**
   * The properties attached to the player's {@link GameProfile}, such as skins or other metadata.
   */
  private @Nullable List<GameProfile.Property> properties;

  /**
   * Whether strict error handling is enabled for the login success packet.
   *
   * <p>This flag controls whether to append the strictness indicator in the packet (1.20.5/1.21+).</p>
   */
  private static final boolean strictErrorHandling = VelocityProperties
          .readBoolean("velocity.strictErrorHandling", true);

  /**
   * Gets the player's UUID from the login success packet.
   *
   * @return the player's UUID
   * @throws IllegalStateException if the UUID is not specified
   */
  public UUID getUuid() {
    if (uuid == null) {
      throw new IllegalStateException("No UUID specified!");
    }

    return uuid;
  }

  public void setUuid(@Nullable final UUID uuid) {
    this.uuid = uuid;
  }

  /**
   * Gets the player's username from the login success packet.
   *
   * @return the player's username
   * @throws IllegalStateException if the username is not specified
   */
  public String getUsername() {
    if (username == null) {
      throw new IllegalStateException("No username specified!");
    }

    return username;
  }

  /**
   * Sets the username of the player.
   *
   * @param username the player's username
   */
  public void setUsername(@Nullable final String username) {
    this.username = username;
  }

  /**
   * Gets the properties associated with the player's game profile.
   *
   * @return the player's {@link GameProfile.Property} list, or {@code null} if none are present
   */
  public @Nullable List<GameProfile.Property> getProperties() {
    return properties;
  }

  /**
   * Sets the properties associated with the player's game profile.
   *
   * @param properties the {@link GameProfile.Property} list
   */
  public void setProperties(@Nullable final List<GameProfile.Property> properties) {
    this.properties = properties;
  }

  @Override
  public final String toString() {
    return "ServerLoginSuccess{"
        + "uuid=" + uuid
        + ", username='" + username + '\''
        + ", properties='" + properties + '\''
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      uuid = ProtocolUtils.readUuid(buf);
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      uuid = ProtocolUtils.readUuidIntArray(buf);
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_7_6)) {
      uuid = UUID.fromString(ProtocolUtils.readString(buf, 36));
    } else {
      uuid = UuidUtils.fromUndashed(ProtocolUtils.readString(buf, 32));
    }

    username = ProtocolUtils.readString(buf, 16);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      properties = ProtocolUtils.readProperties(buf);
    }

    if (version == ProtocolVersion.MINECRAFT_1_20_5 || version == ProtocolVersion.MINECRAFT_1_21) {
      buf.readBoolean();
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (uuid == null) {
      throw new IllegalStateException("No UUID specified!");
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      ProtocolUtils.writeUuid(buf, uuid);
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      ProtocolUtils.writeUuidIntArray(buf, uuid);
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_7_6)) {
      ProtocolUtils.writeString(buf, uuid.toString());
    } else {
      ProtocolUtils.writeString(buf, UuidUtils.toUndashed(uuid));
    }

    if (username == null) {
      throw new IllegalStateException("No username specified!");
    }

    ProtocolUtils.writeString(buf, username);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      if (properties == null) {
        ProtocolUtils.writeVarInt(buf, 0);
      } else {
        ProtocolUtils.writeProperties(buf, properties);
      }
    }

    if (version == ProtocolVersion.MINECRAFT_1_20_5 || version == ProtocolVersion.MINECRAFT_1_21) {
      buf.writeBoolean(strictErrorHandling);
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
