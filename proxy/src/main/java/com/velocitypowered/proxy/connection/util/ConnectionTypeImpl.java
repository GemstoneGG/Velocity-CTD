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

import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.config.PlayerInfoForwarding;
import com.velocitypowered.proxy.connection.ConnectionType;
import com.velocitypowered.proxy.connection.backend.BackendConnectionPhase;
import com.velocitypowered.proxy.connection.client.ClientConnectionPhase;

/**
 * Indicates the type of connection that has been made.
 */
public class ConnectionTypeImpl implements ConnectionType {

  /**
   * The initial connection phase for the client.
   *
   * <p>This phase defines how Velocity will interpret and handle packets sent by the client
   * when the connection is first established (e.g., mod negotiation or handshake state).</p>
   */
  private final ClientConnectionPhase initialClientPhase;

  /**
   * The initial connection phase for the backend server.
   *
   * <p>This phase defines how Velocity will handle and forward packets to the backend server
   * during the early stages of the connection (e.g., Forge handshake state or login state).</p>
   */
  private final BackendConnectionPhase initialBackendPhase;

  /**
   * Constructs a new {@link ConnectionTypeImpl} with the given client and backend phases.
   *
   * @param initialClientPhase  the phase that should be used to interpret the initial client connection
   * @param initialBackendPhase the phase that should be used when connecting to a backend server
   */
  public ConnectionTypeImpl(final ClientConnectionPhase initialClientPhase,
                            final BackendConnectionPhase initialBackendPhase) {
    this.initialClientPhase = initialClientPhase;
    this.initialBackendPhase = initialBackendPhase;
  }

  @Override
  public final ClientConnectionPhase getInitialClientPhase() {
    return initialClientPhase;
  }

  @Override
  public final BackendConnectionPhase getInitialBackendPhase() {
    return initialBackendPhase;
  }

  @SuppressWarnings("checkstyle:DesignForExtension")
  @Override
  public GameProfile addGameProfileTokensIfRequired(final GameProfile original,
                                                    final PlayerInfoForwarding forwardingType) {
    return original;
  }
}
