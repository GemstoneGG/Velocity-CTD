/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.player;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This event is fired when the downstream server tries to remove a resource pack from the player
 * or clear all of them. The proxy will wait on this event to finish before forwarding the
 * action to the user. If this event is denied, no resource packs will be removed from the player.
 */
@AwaitingEvent
public class ServerResourcePackRemoveEvent implements ResultedEvent<ResultedEvent.GenericResult> {

  /**
   * The result determining whether the resource pack removal should proceed.
   */
  private GenericResult result;

  /**
   * The UUID of the resource pack to be removed, or {@code null} if all resource packs should be cleared.
   */
  private final @MonotonicNonNull UUID packId;

  /**
   * The server attempting to remove the resource pack(s) from the player.
   */
  private final ServerConnection serverConnection;

  /**
   * Instantiates this event.
   *
   * @param packId the UUID of the resource pack to remove, or {@code null} to clear all
   * @param serverConnection the server attempting to remove the resource pack
   */
  public ServerResourcePackRemoveEvent(final UUID packId, final ServerConnection serverConnection) {
    this.result = ResultedEvent.GenericResult.allowed();
    this.packId = packId;
    this.serverConnection = serverConnection;
  }

  /**
   * Returns the id of the resource pack, if it's null, all the resource packs
   * from player will be cleared.
   *
   * @return the id
   */
  @Nullable
  public UUID getPackId() {
    return packId;
  }

  /**
   * Returns the server that tries to remove a resource pack from the player or clear all of them.
   *
   * @return the server connection
   */
  public ServerConnection getServerConnection() {
    return serverConnection;
  }

  /**
   * Returns the current {@link GenericResult} that determines whether the resource pack
   * removal should proceed.
   *
   * <p>By default, the result is {@code allowed()}, meaning the resource pack removal
   * or clear will be forwarded to the client unless explicitly denied.</p>
   *
   * @return the current result
   */
  @Override
  public GenericResult getResult() {
    return this.result;
  }

  /**
   * Sets the {@link GenericResult} to control whether the resource pack removal proceeds.
   *
   * <p>Use {@link GenericResult#denied()} to prevent removal, or {@link GenericResult#allowed()}
   * to allow it. If denied, the packet will not be forwarded to the player.</p>
   *
   * @param result the result to apply to this event
   * @throws NullPointerException if {@code result} is null
   */
  @Override
  public void setResult(final GenericResult result) {
    this.result = Preconditions.checkNotNull(result, "result");
  }
}
