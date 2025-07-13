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

package com.velocitypowered.proxy.connection.util;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder.Status;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import java.util.Optional;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;

/**
 * Common connection request results.
 */
public final class ConnectionRequestResults {

  private ConnectionRequestResults() {
    throw new AssertionError();
  }

  /**
   * Creates a successful connection result.
   *
   * @param server the server the connection was made to
   * @return a result indicating the connection succeeded
   */
  public static Impl successful(final RegisteredServer server) {
    return plainResult(Status.SUCCESS, server);
  }

  /**
   * Returns a plain result (one with a status but no reason).
   *
   * @param status the status to use
   * @param server the server to use
   * @return the result
   */
  public static Impl plainResult(final ConnectionRequestBuilder.Status status,
                                 final RegisteredServer server) {
    return new Impl(status, null, server, true);
  }

  /**
   * Returns a disconnect result with a reason.
   *
   * @param component the reason for disconnecting from the server
   * @param server    the server to use
   * @return the result
   */
  public static Impl forDisconnect(final Component component, final RegisteredServer server) {
    return new Impl(Status.SERVER_DISCONNECTED, component, server, true);
  }

  /**
   * Returns a disconnect result using the reason provided by a {@link DisconnectPacket}.
   *
   * @param disconnect the disconnect packet containing the reason
   * @param server     the server the player attempted to connect to
   * @return the result
   */
  public static Impl forDisconnect(final DisconnectPacket disconnect, final RegisteredServer server) {
    return forDisconnect(disconnect.getReason().getComponent(), server);
  }

  /**
   * Returns a disconnect result that is considered unsafe for retrying.
   *
   * <p>This is used in scenarios like Forge handshakes or plugin error conditions where
   * retrying a connection may result in undefined behavior.</p>
   *
   * @param disconnect the disconnect packet containing the reason
   * @param server     the server the player attempted to connect to
   * @return the result marked as unsafe
   */
  public static Impl forUnsafeDisconnect(final DisconnectPacket disconnect, final RegisteredServer server) {
    return new Impl(Status.SERVER_DISCONNECTED, disconnect.getReason().getComponent(), server, false);
  }

  /**
   * Base implementation.
   */
  public static class Impl implements ConnectionRequestBuilder.Result {

    /**
     * The connection attempt status.
     *
     * <p>This indicates whether the attempt to connect to the backend server was successful,
     * already in progress, canceled, or disconnected for another reason.</p>
     */
    private final Status status;

    /**
     * The component describing the reason for the connection result, if provided.
     *
     * <p>This may contain a translated error message to be shown to the player. If {@code null},
     * no specific reason was attached to the result.</p>
     */
    private final @Nullable Component component;

    /**
     * The server that was attempted during the connection.
     *
     * <p>This is the server Velocity tried to connect the player to when the result was generated.</p>
     */
    private final RegisteredServer attemptedConnection;

    /**
     * Indicates whether it is safe to attempt reconnecting to another server after this result.
     *
     * <p>If {@code false}, the proxy should not attempt to connect the player to another server
     * (e.g., due to handshake errors, modded conflicts, or plugin-specific errors).</p>
     */
    private final boolean safe;

    Impl(final Status status, @Nullable final Component component,
         final RegisteredServer attemptedConnection, final boolean safe) {
      this.status = status;
      this.component = component;
      this.attemptedConnection = attemptedConnection;
      this.safe = safe;
    }

    @Override
    public final Status getStatus() {
      return status;
    }

    @Override
    public final Optional<Component> getReasonComponent() {
      return Optional.ofNullable(component);
    }

    @Override
    public final RegisteredServer getAttemptedConnection() {
      return attemptedConnection;
    }

    /**
     * Returns whether it is safe to attempt reconnecting.
     *
     * @return whether we can try to reconnect
     */
    public boolean isSafe() {
      return safe;
    }
  }
}
