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

package com.velocitypowered.proxy.connection.player.resourcepack.handler;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.player.resourcepack.ResourcePackResponseBundle;
import com.velocitypowered.proxy.connection.player.resourcepack.VelocityResourcePackInfo;
import com.velocitypowered.proxy.protocol.packet.ResourcePackRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ResourcePackResponsePacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBufUtil;
import java.util.Collection;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles the process of sending and tracking resource packs to the player.
 *
 * <p>This class provides a version-specific implementation for both legacy and modern Minecraft
 * clients, managing queued and applied resource packs, as well as the handling of client responses
 * to those packs.</p>
 *
 * <p>Subclasses of this class should implement the logic for specific protocol versions.</p>
 */
public abstract sealed class ResourcePackHandler permits LegacyResourcePackHandler, ModernResourcePackHandler {

  protected final ConnectedPlayer player;

  protected final VelocityServer server;

  protected ResourcePackHandler(ConnectedPlayer player, VelocityServer server) {
    this.player = player;
    this.server = server;
  }

  /**
   * Creates a new ResourcePackHandler.
   *
   * @param player the player.
   * @param server the velocity server
   *
   * @return a new ResourcePackHandler
   */
  public static @NotNull ResourcePackHandler create(ConnectedPlayer player, VelocityServer server) {
    ProtocolVersion protocolVersion = player.getProtocolVersion();
    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_17)) {
      return new LegacyResourcePackHandler(player, server);
    }

    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      return new Legacy117ResourcePackHandler(player, server);
    }

    return new ModernResourcePackHandler(player, server);
  }

  public abstract @Nullable ResourcePackInfo getFirstAppliedPack();

  public abstract @Nullable ResourcePackInfo getFirstPendingPack();

  public abstract @NotNull Collection<ResourcePackInfo> getAppliedResourcePacks();

  public abstract @NotNull Collection<ResourcePackInfo> getPendingResourcePacks();

  /**
   * Clears the applied resource pack field.
   */
  public abstract void clearAppliedResourcePacks();

  public abstract boolean remove(UUID id);

  /**
   * Queues a resource-pack for sending to the player and sends it immediately if the queue is
   * empty.
   *
   * @param info the resource pack to queue
   */
  public abstract void queueResourcePack(@NotNull ResourcePackInfo info);

  /**
   * Queues a resource-request for sending to the player and sends it immediately if the queue is
   * empty.
   *
   * @param request the resource pack request
   */
  public void queueResourcePack(@NotNull ResourcePackRequest request) {
    for (net.kyori.adventure.resource.ResourcePackInfo pack : request.packs()) {
      ResourcePackInfo resourcePackInfo = VelocityResourcePackInfo.fromAdventureRequest(request, pack);
      this.checkAlreadyAppliedPack(resourcePackInfo.getHash());
      queueResourcePack(resourcePackInfo);
    }
  }

  protected void sendResourcePackRequestPacket(@NotNull ResourcePackInfo queued) {
    ResourcePackRequestPacket request = new ResourcePackRequestPacket();
    request.setId(queued.getId());
    request.setUrl(queued.getUrl());
    if (queued.getHash() != null) {
      request.setHash(ByteBufUtil.hexDump(queued.getHash()));
    } else {
      request.setHash("");
    }

    request.setRequired(queued.getShouldForce());
    request.setPrompt(queued.getPrompt() == null
            ? null : new ComponentHolder(player.getProtocolVersion(), player.translateMessage(queued.getPrompt())));

    player.getConnection().write(request);
  }

  /**
   * Processes a client response to a "sent" resource-pack.
   *
   * <p>Cases in which no action will be taken:</p>
   *
   * <ul>
   *   <li><b>DOWNLOADED</b><br>
   *       In this case, the resource pack is downloaded and will be applied to the client;
   *       no action is required in Velocity.
   *   </li>
   *
   *   <li><b>INVALID_URL</b><br>
   *       In this case, the client has received a resource pack request,
   *       and the first check it performs is if the URL is valid, if not,
   *       it will return this value
   *   </li>
   *
   *   <li><b>FAILED_RELOAD</b><br>
   *       In this case, when trying to reload the client's resources,
   *       an error occurred while reloading a resource pack
   *   </li>
   *
   *   <li><b>DECLINED</b><br>
   *       Only in modern versions, as the resource pack has already been rejected,
   *       there is nothing to do. If the resource pack is required,
   *       the client will be kicked out of the server.
   *   </li>
   * </ul>
   *
   * @param bundle the resource pack response bundle
   * @return whether the response was handled
   */
  public abstract boolean onResourcePackResponse(@NotNull ResourcePackResponseBundle bundle);

  protected boolean handleResponseResult(@Nullable ResourcePackInfo queued,
                                         @NotNull ResourcePackResponseBundle bundle) {
    // If Velocity, through a plugin, has sent a resource pack to the client,
    // there is no need to report the status of the response to the server
    // since it has no information that a resource pack has been sent
    boolean handled = queued != null && queued.getOriginalOrigin() == ResourcePackInfo.Origin.PLUGIN_ON_PROXY;
    if (!handled) {
      VelocityServerConnection connectionInFlight = player.getConnectionInFlight();
      if (connectionInFlight != null && connectionInFlight.getConnection() != null) {
        connectionInFlight.getConnection().write(new ResourcePackResponsePacket(bundle.uuid(), bundle.hash(), bundle.status()));
      }
    }
    return handled;
  }

  /**
   * Checks if a resource pack has already been applied based on its hash.
   *
   * @param hash the resource pack hash
   * @return {@code true} if a pack with the same hash has already been applied
   */
  public abstract boolean hasPackAppliedByHash(byte[] hash);

  public void checkAlreadyAppliedPack(byte[] hash) {
    if (this.hasPackAppliedByHash(hash)) {
      throw new IllegalStateException("Cannot apply a resource pack already applied");
    }
  }
}
