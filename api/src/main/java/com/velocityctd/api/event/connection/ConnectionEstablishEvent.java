/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.event.connection;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.proxy.InboundConnection;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This event is fired when a new connection is initially established with the proxy.
 *
 * <p>Unlike most events, this event is marked with {@link AwaitingEvent}, which means Velocity
 * will block connection progression until all event handlers have completed. This provides
 * an opportunity to validate or reject the connection before the login sequence begins.</p>
 */
@AwaitingEvent
public final class ConnectionEstablishEvent implements ResultedEvent<ResultedEvent.GenericResult> {

  /**
   * The inbound connection that is being established with the proxy.
   */
  private final InboundConnection connection;

  /**
   * The handshake intention of the client, or {@code null} if not yet determined.
   */
  private final HandshakeIntent intention;

  /**
   * The result of this event, controlling whether the connection is allowed to continue.
   */
  private GenericResult result = GenericResult.allowed();

  /**
   * Constructs a new {@link ConnectionEstablishEvent}.
   *
   * @param connection the inbound connection being established, must not be null
   * @param intention  the handshake intention of the client, or {@code null} if not yet determined
   * @throws NullPointerException if {@code connection} is null
   */
  public ConnectionEstablishEvent(InboundConnection connection,
                                  @Nullable HandshakeIntent intention) {
    this.connection = Preconditions.checkNotNull(connection, "connection");
    this.intention = intention;
  }

  /**
   * Gets the inbound connection that is in the process of being established.
   *
   * @return the inbound connection
   */
  public InboundConnection getConnection() {
    return this.connection;
  }

  /**
   * Gets the handshake intention of the client. The intention may indicate, for example,
   * whether the client is attempting to log in or request the server list.
   *
   * @return the handshake intention, or {@code null} if not yet determined
   */
  public @Nullable HandshakeIntent getIntention() {
    return this.intention;
  }

  /**
   * Returns the current result of this event. By default, connections are allowed.
   *
   * @return the event result
   */
  @Override
  public GenericResult getResult() {
    return this.result;
  }

  /**
   * Sets the result of this event. Use this to allow or deny the connection.
   *
   * <p>If a connection is denied, the client will be immediately disconnected.</p>
   *
   * @param result the new result to apply, must not be null
   * @throws NullPointerException if {@code result} is null
   */
  @Override
  public void setResult(GenericResult result) {
    this.result = Preconditions.checkNotNull(result, "result");
  }

  @Override
  public String toString() {
    return "ConnectionEstablishEvent{"
        + "connection=" + connection
        + ", intention=" + intention
        + ", result=" + result
        + '}';
  }
}
