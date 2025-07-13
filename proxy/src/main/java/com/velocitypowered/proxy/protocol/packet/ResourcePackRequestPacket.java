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

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.player.resourcepack.VelocityResourcePackInfo;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import java.util.UUID;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a resource pack request packet sent by the server to prompt the client to download a resource pack.
 * The packet includes the resource pack URL, SHA1 hash, and optional prompt.
 */
public class ResourcePackRequestPacket implements MinecraftPacket {

  /**
   * The unique identifier of the resource pack request (1.20.3+), or {@code null} if not set.
   */
  private @MonotonicNonNull UUID id;

  /**
   * The URL where the client should download the resource pack.
   */
  private @MonotonicNonNull String url;

  /**
   * The SHA1 hash of the resource pack, used to verify its integrity.
   */
  private @MonotonicNonNull String hash;

  /**
   * Whether the resource pack is required to join the server (1.17+).
   */
  private boolean isRequired;

  /**
   * An optional prompt component displayed to the user (1.17+).
   */
  private @Nullable ComponentHolder prompt;

  /**
   * Pattern used to validate that the provided resource pack SHA1 hash is plausibly correct.
   * Matches a 40-character lowercase hexadecimal string.
   *
   * <p>Introduced for validation starting in Minecraft 1.20.2+.</p>
   */
  private static final Pattern PLAUSIBLE_SHA1_HASH = Pattern.compile("^[a-z0-9]{40}$");

  /**
   * Gets the unique ID for the resource pack request (1.20.3+).
   *
   * @return the UUID, or {@code null} if not set
   */
  public @Nullable UUID getId() {
    return id;
  }

  /**
   * Sets the unique ID for the resource pack request.
   *
   * @param id the UUID to set
   */
  public void setId(final UUID id) {
    this.id = id;
  }

  /**
   * Gets the URL of the resource pack.
   *
   * @return the URL string, or {@code null} if not set
   */
  public @Nullable String getUrl() {
    return url;
  }

  /**
   * Sets the URL where the client should fetch the resource pack.
   *
   * @param url the URL string
   */
  public void setUrl(final String url) {
    this.url = url;
  }

  /**
   * Returns whether the resource pack is required.
   *
   * @return {@code true} if the pack is required, {@code false} otherwise
   */
  public boolean isRequired() {
    return isRequired;
  }

  /**
   * Gets the SHA1 hash of the resource pack.
   *
   * @return the hash string, or {@code null} if not set
   */
  public @Nullable String getHash() {
    return hash;
  }

  /**
   * Sets the SHA1 hash of the resource pack.
   *
   * @param hash the hash string
   */
  public void setHash(final String hash) {
    this.hash = hash;
  }

  /**
   * Sets whether the resource pack is required.
   *
   * @param required {@code true} if required, {@code false} otherwise
   */
  public void setRequired(final boolean required) {
    isRequired = required;
  }

  /**
   * Gets the prompt message to display to the player.
   *
   * @return the {@link ComponentHolder} representing the prompt, or {@code null} if not present
   */
  public @Nullable ComponentHolder getPrompt() {
    return prompt;
  }

  /**
   * Sets the prompt message to display to the player.
   *
   * @param prompt the {@link ComponentHolder}, or {@code null} if no prompt should be displayed
   */
  public void setPrompt(@Nullable final ComponentHolder prompt) {
    this.prompt = prompt;
  }

  @Override
  public final void decode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      this.id = ProtocolUtils.readUuid(buf);
    }

    this.url = ProtocolUtils.readString(buf);
    this.hash = ProtocolUtils.readString(buf);
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_17)) {
      this.isRequired = buf.readBoolean();
      if (buf.readBoolean()) {
        this.prompt = ComponentHolder.read(buf, protocolVersion);
      } else {
        this.prompt = null;
      }
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final Direction direction, final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      if (id == null) {
        throw new IllegalStateException("Resource pack proxyId not set yet!");
      }

      ProtocolUtils.writeUuid(buf, id);
    }

    if (url == null || hash == null) {
      throw new IllegalStateException("Packet not fully filled in yet!");
    }

    ProtocolUtils.writeString(buf, url);
    ProtocolUtils.writeString(buf, hash);
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_17)) {
      buf.writeBoolean(isRequired);
      if (prompt != null) {
        buf.writeBoolean(true);
        prompt.write(buf);
      } else {
        buf.writeBoolean(false);
      }
    }
  }

  /**
   * Converts this packet into a {@link VelocityResourcePackInfo} object, which contains the information
   * about the resource pack being requested.
   *
   * @return a {@code VelocityResourcePackInfo} representing the resource pack information
   */
  public VelocityResourcePackInfo toServerPromptedPack() {
    final ResourcePackInfo.Builder builder =
        new VelocityResourcePackInfo.BuilderImpl(Preconditions.checkNotNull(url))
            .setId(id).setPrompt(prompt == null ? null : prompt.getComponent())
            .setShouldForce(isRequired).setOrigin(ResourcePackInfo.Origin.DOWNSTREAM_SERVER);

    if (hash != null && !hash.isEmpty()) {
      if (PLAUSIBLE_SHA1_HASH.matcher(hash).matches()) {
        builder.setHash(ByteBufUtil.decodeHexDump(hash));
      }
    }

    return (VelocityResourcePackInfo) builder.build();
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public final String toString() {
    return "ResourcePackRequestPacket{"
        + "id=" + id
        + ", url='" + url + '\''
        + ", hash='" + hash + '\''
        + ", isRequired=" + isRequired
        + ", prompt=" + prompt
        + '}';
  }
}
