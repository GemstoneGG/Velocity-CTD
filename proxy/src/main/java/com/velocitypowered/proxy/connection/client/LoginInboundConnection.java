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

package com.velocitypowered.proxy.connection.client;

import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.LoginPhaseConnection;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.crypto.KeyIdentifiable;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.protocol.packet.LoginPluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponsePacket;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import space.vectrix.flare.fastutil.Int2ObjectSyncMap;

/**
 * Handles the actual login stage of a player logging in.
 */
public class LoginInboundConnection implements LoginPhaseConnection, KeyIdentifiable {

  /**
   * Atomic updater used to generate unique plugin message IDs.
   */
  private static final AtomicIntegerFieldUpdater<LoginInboundConnection> SEQUENCE_UPDATER =
      AtomicIntegerFieldUpdater.newUpdater(LoginInboundConnection.class, "sequenceCounter");

  /**
   * The connection delegate used for low-level access to connection state and networking.
   */
  private final InitialInboundConnection delegate;

  /**
   * Stores outstanding plugin message responses by ID, waiting for client replies.
   */
  private final Int2ObjectMap<MessageConsumer> outstandingResponses;

  /**
   * Sequence counter for assigning unique plugin message IDs.
   */
  private volatile int sequenceCounter;

  /**
   * Queue of login plugin messages to be sent once the login event has fired.
   */
  private final Queue<LoginPluginMessagePacket> loginMessagesToSend;

  /**
   * Task to run once all login plugin messages have been responded to.
   */
  private volatile Runnable onAllMessagesHandled;

  /**
   * Whether the login event has already been fired.
   */
  private volatile boolean loginEventFired;

  /**
   * The identified key used for cryptographic identity verification.
   */
  private @MonotonicNonNull IdentifiedKey playerKey;

  LoginInboundConnection(final InitialInboundConnection delegate) {
    this.delegate = delegate;
    this.outstandingResponses = Int2ObjectSyncMap.hashmap();
    this.loginMessagesToSend = new ConcurrentLinkedQueue<>();
  }

  /**
   * Gets the IP address of the connecting client.
   *
   * @return the client's remote socket address
   */
  @Override
  public InetSocketAddress getRemoteAddress() {
    return delegate.getRemoteAddress();
  }

  /**
   * Returns the virtual host that the client attempted to connect to.
   *
   * @return an {@link Optional} containing the virtual host, if available
   */
  @Override
  public Optional<InetSocketAddress> getVirtualHost() {
    return delegate.getVirtualHost();
  }

  /**
   * Returns the string form of the virtual host the client used to connect.
   *
   * @return an {@link Optional} containing the raw virtual host string
   */
  @Override
  public Optional<String> getRawVirtualHost() {
    return delegate.getRawVirtualHost();
  }

  /**
   * Returns whether the connection is currently active and open.
   *
   * @return {@code true} if the connection is active
   */
  @Override
  public boolean isActive() {
    return delegate.isActive();
  }

  /**
   * Gets the Minecraft protocol version used by the client.
   *
   * @return the client's protocol version
   */
  @Override
  public ProtocolVersion getProtocolVersion() {
    return delegate.getProtocolVersion();
  }

  /**
   * Sends a login plugin message to the client during the login phase (1.13+).
   *
   * <p>The {@link MessageConsumer} will be called asynchronously once the client replies,
   * or skipped if the client disconnects or fails to respond.</p>
   *
   * @param identifier the plugin message channel
   * @param contents the message payload
   * @param consumer the consumer to handle the response
   * @throws NullPointerException if any argument is {@code null}
   * @throws IllegalStateException if the client protocol is older than Minecraft 1.13
   */
  @Override
  public void sendLoginPluginMessage(final ChannelIdentifier identifier, final byte[] contents,
                                     final MessageConsumer consumer) {
    if (identifier == null) {
      throw new NullPointerException("identifier");
    }

    if (contents == null) {
      throw new NullPointerException("contents");
    }

    if (consumer == null) {
      throw new NullPointerException("consumer");
    }

    if (delegate.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_13)) {
      throw new IllegalStateException("Login plugin messages can only be sent to clients running "
          + "Minecraft 1.13 and above");
    }

    final int id = SEQUENCE_UPDATER.incrementAndGet(this);
    this.outstandingResponses.put(id, consumer);

    final LoginPluginMessagePacket message = new LoginPluginMessagePacket(id, identifier.getId(),
        Unpooled.wrappedBuffer(contents));
    if (!this.loginEventFired) {
      this.loginMessagesToSend.add(message);
    } else {
      this.delegate.getConnection().write(message);
    }
  }

  /**
   * Disconnects the connection from the server.
   *
   * @param reason the reason for disconnecting
   */
  public void disconnect(final Component reason) {
    this.delegate.disconnect(reason);
    this.cleanup();
  }

  /**
   * Clears all pending login plugin messages and responses, and removes completion handlers.
   *
   * <p>This should be called during disconnect or session teardown to release resources.</p>
   */
  void cleanup() {
    this.loginMessagesToSend.clear();
    this.outstandingResponses.clear();
    this.onAllMessagesHandled = null;
  }

  /**
   * Handles an incoming {@link LoginPluginResponsePacket} from the client.
   *
   * <p>If the response matches a pending message ID, the associated {@link MessageConsumer}
   * is invoked with the payload or {@code null} if the response failed.</p>
   *
   * @param response the login plugin response received from the client
   */
  void handleLoginPluginResponse(final LoginPluginResponsePacket response) {
    final MessageConsumer consumer = this.outstandingResponses.remove(response.getId());
    if (consumer != null) {
      try {
        consumer.onMessageResponse(response.isSuccess() ? ByteBufUtil.getBytes(response.content()) : null);
      } finally {
        final Runnable onAllMessagesHandled = this.onAllMessagesHandled;
        if (this.outstandingResponses.isEmpty() && onAllMessagesHandled != null) {
          onAllMessagesHandled.run();
        }
      }
    }
  }

  /**
   * Marks the login event as fired and begins sending any queued login plugin messages.
   *
   * <p>If no plugin messages are queued, the given {@code onAllMessagesHandled} callback
   * will be executed immediately.</p>
   *
   * @param onAllMessagesHandled the callback to run once all plugin message responses have been handled
   */
  void loginEventFired(final Runnable onAllMessagesHandled) {
    this.loginEventFired = true;
    this.onAllMessagesHandled = onAllMessagesHandled;
    if (!this.loginMessagesToSend.isEmpty()) {
      LoginPluginMessagePacket message;
      while ((message = this.loginMessagesToSend.poll()) != null) {
        this.delegate.getConnection().delayedWrite(message);
      }

      this.delegate.getConnection().flush();
    } else {
      onAllMessagesHandled.run();
    }
  }

  /**
   * Returns the underlying {@link MinecraftConnection} used by this login session.
   *
   * <p>This method provides access to the Netty channel and other low-level connection details.</p>
   *
   * @return the Minecraft connection associated with the delegate
   */
  MinecraftConnection delegatedConnection() {
    return delegate.getConnection();
  }

  /**
   * Sets the player's {@link IdentifiedKey} used for secure identity validation.
   *
   * @param playerKey the player's identified key
   */
  public void setPlayerKey(final IdentifiedKey playerKey) {
    this.playerKey = playerKey;
  }

  /**
   * Returns the {@link IdentifiedKey} used for validating the player's identity, if available.
   *
   * @return the player's identified key, or {@code null} if not set
   */
  @Override
  public IdentifiedKey getIdentifiedKey() {
    return playerKey;
  }

  /**
   * Gets the current {@link ProtocolState} of this connection.
   *
   * @return the current protocol state
   */
  @Override
  public ProtocolState getProtocolState() {
    return delegate.getProtocolState();
  }

  /**
   * Gets the {@link HandshakeIntent} sent by the client during connection initiation.
   *
   * @return the client's handshake intent
   */
  @Override
  public HandshakeIntent getHandshakeIntent() {
    return delegate.getHandshakeIntent();
  }
}
