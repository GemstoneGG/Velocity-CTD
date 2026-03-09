/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.connection;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.proxy.InboundConnection;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This event is fired when a new connection is initially established with the proxy.
 *
 * <p>Unlike most events, this event is marked with {@link AwaitingEvent}, which means Velocity
 * will block connection progression until all event handlers have completed. This provides
 * an opportunity to validate or reject the connection before the login sequence begins.</p>
 */
@AwaitingEvent
public class ConnectionEstablishEvent implements ResultedEvent<ResultedEvent.GenericResult> {

  /**
   * The inbound connection that is being established with the proxy.
   *
   * <p>This object exposes information about the remote client such as its
   * address and protocol version, and can be used to make decisions about
   * whether the connection should be accepted.</p>
   */
  private final InboundConnection connection;

  /**
   * The intention of the client during the handshake phase.
   *
   * <p>This may indicate whether the client is attempting to log in, ping the
   * server list, or perform another handshake action. May be {@code null}
   * if the handshake intent has not yet been determined.</p>
   */
  private final HandshakeIntent intention;

  /**
   * The result of this event, controlling whether the connection is allowed
   * to continue or is denied by the proxy.
   *
   * <p>Defaults to {@link GenericResult#allowed()} if not otherwise set.</p>
   */
  private GenericResult result = GenericResult.allowed();

  /**
   * Constructs a new {@link ConnectionEstablishEvent}.
   *
   * @param connection the inbound connection being established, must not be null
   * @param intention  the handshake intention of the client, or {@code null} if not yet determined
   * @throws NullPointerException if {@code connection} is null
   */
  public ConnectionEstablishEvent(final @NonNull InboundConnection connection,
                                  final @Nullable HandshakeIntent intention) {
    this.connection = Preconditions.checkNotNull(connection, "connection");
    this.intention = intention;
  }

  /**
   * Gets the inbound connection that is in the process of being established.
   *
   * @return the inbound connection
   */
  public @NonNull InboundConnection getConnection() {
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
  public void setResult(final @NonNull GenericResult result) {
    this.result = Preconditions.checkNotNull(result, "result");
  }
}
