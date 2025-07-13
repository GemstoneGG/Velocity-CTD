/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.player;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;

/**
 * This event is fired when the downstream server tries to send a player a ResourcePack packet.
 * The proxy will wait on this event to finish before forwarding the resource pack to the user.
 * If this event is denied, it will retroactively send a DENIED status to the downstream
 * server in response.
 * If the downstream server has it set to "forced," it will forcefully disconnect the user.
 */
@AwaitingEvent
public class ServerResourcePackSendEvent implements ResultedEvent<ResultedEvent.GenericResult> {

  /**
   * The result determining whether the resource pack should be forwarded to the player.
   */
  private GenericResult result;

  /**
   * The original resource pack sent by the server.
   */
  private final ResourcePackInfo receivedResourcePack;

  /**
   * The resource pack that will actually be sent to the player.
   * This may be modified by plugins from the original.
   */
  private ResourcePackInfo providedResourcePack;

  /**
   * The server connection that attempted to send the resource pack.
   */
  private final ServerConnection serverConnection;

  /**
   * Constructs a new ServerResourcePackSendEvent.
   *
   * @param receivedResourcePack The resource pack the server sent.
   * @param serverConnection The connection this occurred on.
   */
  public ServerResourcePackSendEvent(final ResourcePackInfo receivedResourcePack, final ServerConnection serverConnection) {
    this.result = ResultedEvent.GenericResult.allowed();
    this.receivedResourcePack = receivedResourcePack;
    this.serverConnection = serverConnection;
    this.providedResourcePack = receivedResourcePack;
  }

  /**
   * Gets the server connection that attempted to send the resource pack.
   *
   * @return the server connection
   */
  public ServerConnection getServerConnection() {
    return serverConnection;
  }

  /**
   * Gets the original resource pack provided by the server.
   *
   * @return the resource pack sent by the server
   */
  public ResourcePackInfo getReceivedResourcePack() {
    return receivedResourcePack;
  }

  /**
   * Gets the resource pack that will be forwarded to the player.
   * This can be different from the one received from the server.
   *
   * @return the resource pack to send to the player
   */
  public ResourcePackInfo getProvidedResourcePack() {
    return providedResourcePack;
  }

  /**
   * Sets the resource pack that should be forwarded to the player.
   *
   * @param providedResourcePack the resource pack to send to the player
   */
  public void setProvidedResourcePack(final ResourcePackInfo providedResourcePack) {
    this.providedResourcePack = providedResourcePack;
  }

  @Override
  public final GenericResult getResult() {
    return this.result;
  }

  @Override
  public final void setResult(final GenericResult result) {
    this.result = result;
  }
}
