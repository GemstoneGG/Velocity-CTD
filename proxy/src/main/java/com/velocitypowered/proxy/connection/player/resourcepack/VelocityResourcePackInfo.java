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

package com.velocitypowered.proxy.connection.player.resourcepack;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import io.netty.buffer.ByteBufUtil;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Implements {@link ResourcePackInfo}.
 */
public final class VelocityResourcePackInfo implements ResourcePackInfo {

  /**
   * The unique identifier of the resource pack.
   */
  private final UUID id;

  /**
   * The URL where the resource pack is hosted.
   */
  private final String url;

  /**
   * The SHA-1 hash of the resource pack contents (optional, may be {@code null}).
   */
  private final byte @Nullable [] hash;

  /**
   * Whether the pack should be forced to apply on the client.
   */
  private final boolean shouldForce;

  /**
   * The optional prompt shown to the user when accepting the pack (1.17+).
   */
  private final @Nullable Component prompt;

  /**
   * The effective origin of this pack (plugin/server/etc).
   */
  private final Origin origin;

  /**
   * The original origin of this pack (used to detect proxy modifications).
   */
  private Origin originalOrigin;

  private VelocityResourcePackInfo(final UUID id, final String url, final byte @Nullable [] hash, final boolean shouldForce,
                                   @Nullable final Component prompt, final Origin origin) {
    this.id = id;
    this.url = url;
    this.hash = hash;
    this.shouldForce = shouldForce;
    this.prompt = prompt;
    this.origin = origin;
    this.originalOrigin = origin;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public @Nullable Component getPrompt() {
    return prompt;
  }

  @Override
  public boolean getShouldForce() {
    return shouldForce;
  }

  @Override
  public byte @Nullable[] getHash() {
    return hash == null ? null : hash.clone(); // Thanks spotbugs, very helpful.
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  /**
   * Sets the original origin of this pack.
   *
   * @param originalOrigin the original origin to record
   */
  public void setOriginalOrigin(final Origin originalOrigin) {
    this.originalOrigin = originalOrigin;
  }

  @Override
  public Origin getOriginalOrigin() {
    return originalOrigin;
  }

  @Override
  public Builder asBuilder() {
    return new BuilderImpl(url)
        .setId(id)
        .setShouldForce(shouldForce)
        .setHash(hash)
        .setPrompt(prompt);
  }

  @Override
  public Builder asBuilder(final String newUrl) {
    return new BuilderImpl(newUrl)
        .setId(id)
        .setShouldForce(shouldForce)
        .setHash(hash)
        .setPrompt(prompt);
  }

  @Override
  public @NotNull ResourcePackRequest asResourcePackRequest() {
    return ResourcePackRequest.resourcePackRequest()
        .packs(net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo()
            .id(this.id)
            .uri(URI.create(this.url))
            .hash(this.hash == null ? "" : ByteBufUtil.hexDump(this.hash))
            .build())
        .required(this.shouldForce)
        .prompt(this.prompt)
        .build();
  }

  /**
   * Converts a {@link net.kyori.adventure.resource.ResourcePackInfo} from an Adventure resource pack request
   * into a {@link ResourcePackInfo}.
   *
   * @param request the {@link ResourcePackRequest} containing details of the resource pack request
   * @param pack the {@link net.kyori.adventure.resource.ResourcePackInfo} instance to convert from
   * @return a new {@link ResourcePackInfo} representing the converted Adventure resource pack request
   */
  public static ResourcePackInfo fromAdventureRequest(final ResourcePackRequest request,
                                                      final net.kyori.adventure.resource.ResourcePackInfo pack) {
    return new BuilderImpl(pack.uri().toString())
        .setHash(pack.hash().isEmpty() ? null : ByteBufUtil.decodeHexDump(pack.hash()))
        .setId(pack.id())
        .setShouldForce(request.required())
        .setPrompt(request.prompt())
        .build();
  }

  /**
   * Implements the builder for {@link ResourcePackInfo} instances.
   */
  public static final class BuilderImpl implements ResourcePackInfo.Builder {

    /**
     * The unique identifier of the resource pack.
     *
     * <p>By default, this is generated from the resource pack URL using {@link UUID#nameUUIDFromBytes(byte[])}.</p>
     */
    private UUID id;

    /**
     * The resource pack download URL.
     */
    private final String url;

    /**
     * Whether the resource pack must be applied by the client.
     */
    private boolean shouldForce;

    /**
     * The optional SHA-1 hash of the resource pack, or {@code null} if not specified.
     */
    private byte @Nullable [] hash;

    /**
     * The optional prompt component displayed to the user (1.17+), or {@code null} if absent.
     */
    private @Nullable Component prompt;

    /**
     * The declared origin of the resource pack. Defaults to {@link Origin#PLUGIN_ON_PROXY}.
     */
    private Origin origin = Origin.PLUGIN_ON_PROXY;

    /**
     * Constructs a new builder for a resource pack targeting the specified URL.
     *
     * <p>By default, the ID is derived from the URL using {@link UUID#nameUUIDFromBytes(byte[])}, but
     * can be overridden using {@link #setId(UUID)}.</p>
     *
     * @param url the resource pack download URL
     * @throws NullPointerException if {@code url} is {@code null}
     */
    public BuilderImpl(final String url) {
      this.url = Preconditions.checkNotNull(url, "url");
      this.id = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public BuilderImpl setId(final UUID id) {
      this.id = id;
      return this;
    }

    @Override
    public BuilderImpl setShouldForce(final boolean shouldForce) {
      this.shouldForce = shouldForce;
      return this;
    }

    @Override
    public BuilderImpl setHash(final byte @Nullable [] hash) {
      if (hash != null) {
        Preconditions.checkArgument(hash.length == 20, "Hash length is not 20");
        this.hash = hash.clone(); // Thanks spotbugs, very helpful.
      } else {
        this.hash = null;
      }

      return this;
    }

    @Override
    public BuilderImpl setPrompt(@Nullable final Component prompt) {
      this.prompt = prompt;
      return this;
    }

    @Override
    public ResourcePackInfo build() {
      return new VelocityResourcePackInfo(id, url, hash, shouldForce, prompt, origin);
    }

    /**
     * Sets the origin of the resource pack.
     *
     * <p>This indicates where the resource pack originated from, such as a plugin or the backend server.</p>
     *
     * @param origin the {@link Origin} of the resource pack
     * @return this builder instance
     */
    public BuilderImpl setOrigin(final Origin origin) {
      this.origin = origin;
      return this;
    }
  }
}
