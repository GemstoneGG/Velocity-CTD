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
import com.velocitypowered.api.proxy.InboundConnection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This event is fired when a player has initiated a connection with the proxy but before the proxy
 * authenticates the player with Mojang or before the player's proxy connection is fully established
 * (for offline mode). Velocity will wait for this event to finish firing before proceeding further
 * with the login process, but you should try to limit the work done in any event that fires during
 * the login process.
 *
 * <p>As of Velocity 3.1.0, you may cast the {@link InboundConnection} to a
 * {@link com.velocitypowered.api.proxy.LoginPhaseConnection} to allow a
 * proxy plugin to send login plugin messages to the client.</p>
 */
@AwaitingEvent
public final class PreLoginEvent implements ResultedEvent<PreLoginEvent.PreLoginComponentResult> {

  /**
   * The inbound connection associated with this pre-login attempt.
   */
  private final InboundConnection connection;

  /**
   * The username provided by the connecting player.
   */
  private final String username;

  /**
   * The UUID of the connecting player, if available.
   */
  private final @Nullable UUID uuid;

  /**
   * The result of the pre-login event, indicating whether the player is allowed to proceed.
   */
  private PreLoginComponentResult result;

  /**
   * Creates a new instance, without an associated UUID.
   *
   * @param connection the connection logging into the proxy
   * @param username the player's username
   * @deprecated use {@link #PreLoginEvent(InboundConnection, String, UUID)}
   */
  @Deprecated
  public PreLoginEvent(final InboundConnection connection, final String username) {
    this(connection, username, null);
  }

  /**
   * Creates a new instance.
   *
   * @param connection the connection logging into the proxy
   * @param username the player's username
   * @param uuid the player's uuid, if known
   */
  public PreLoginEvent(final InboundConnection connection, final String username, final @Nullable UUID uuid) {
    this.connection = Preconditions.checkNotNull(connection, "connection");
    this.username = Preconditions.checkNotNull(username, "username");
    this.uuid = uuid;
    this.result = PreLoginComponentResult.allowed();
  }

  /**
   * Gets the inbound connection associated with this login attempt.
   *
   * @return the inbound connection
   */
  public InboundConnection getConnection() {
    return connection;
  }

  /**
   * Gets the username of the player attempting to connect.
   *
   * @return the player's username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Returns the UUID of the connecting player.
   *
   * <p>This value is {@code null} on 1.19.2 and lower,
   * up to 1.20.1 it is optional and from 1.20.2 it will always be available.</p>
   *
   * @return the uuid
   * @since Minecraft 1.19.3
   */
  public @Nullable UUID getUniqueId() {
    return uuid;
  }

  @Override
  public PreLoginComponentResult getResult() {
    return result;
  }

  @Override
  public void setResult(final @NonNull PreLoginComponentResult result) {
    this.result = Preconditions.checkNotNull(result, "result");
  }

  @Override
  public String toString() {
    return "PreLoginEvent{"
        + "connection=" + connection
        + ", username='" + username + '\''
        + ", result=" + result
        + '}';
  }

  /**
   * Represents an "allowed/allowed with forced online\offline mode/denied" result with a reason
   * allowed for denial.
   */
  public static final class PreLoginComponentResult implements ResultedEvent.Result {

    /**
     * A result allowing the player to connect normally.
     */
    private static final PreLoginComponentResult ALLOWED = new PreLoginComponentResult(
        Result.ALLOWED, null);

    /**
     * A result allowing the player to connect and forcing online mode for authentication.
     */
    private static final PreLoginComponentResult FORCE_ONLINEMODE = new PreLoginComponentResult(
        Result.FORCE_ONLINE, null);

    /**
     * A result allowing the player to connect and forcing offline mode.
     */
    private static final PreLoginComponentResult FORCE_OFFLINEMODE = new PreLoginComponentResult(
        Result.FORCE_OFFLINE, null);

    /**
     * The login result type (e.g., allowed, denied, forced mode).
     */
    private final Result result;

    /**
     * The message to show to the player if the login is denied.
     */
    private final Component reason;

    private PreLoginComponentResult(final Result result,
                                    final @Nullable Component reason) {
      this.result = result;
      this.reason = reason;
    }

    @Override
    public boolean isAllowed() {
      return result != Result.DISALLOWED;
    }

    /**
     * Gets the reason component shown to the player if the connection is denied.
     *
     * @return the reason as a {@link Component}, or empty if not denied
     */
    public Optional<Component> getReasonComponent() {
      return Optional.ofNullable(reason);
    }

    /**
     * Checks if this result explicitly forces online mode for the connection.
     *
     * @return true if online mode is forced
     */
    public boolean isOnlineModeAllowed() {
      return result == Result.FORCE_ONLINE;
    }

    /**
     * Checks if this result explicitly forces offline mode for the connection.
     *
     * @return true if offline mode is forced
     */
    public boolean isForceOfflineMode() {
      return result == Result.FORCE_OFFLINE;
    }

    @Override
    public String toString() {
      return switch (result) {
        case ALLOWED -> "allowed";
        case FORCE_OFFLINE -> "allowed with force offline mode";
        case FORCE_ONLINE -> "allowed with online mode";
        default -> "denied";
      };
    }

    /**
     * Returns a result indicating the connection will be allowed through the proxy.
     *
     * @return the allowed result
     */
    public static PreLoginComponentResult allowed() {
      return ALLOWED;
    }

    /**
     * Returns a result indicating the connection will be allowed through the proxy, but the
     * connection will be forced to use online mode if the proxy is in offline mode. This
     * acts similarly to {@link #allowed()} on an online-mode proxy.
     *
     * @return the result
     */
    public static PreLoginComponentResult forceOnlineMode() {
      return FORCE_ONLINEMODE;
    }

    /**
     * Returns a result indicating the connection will be allowed through the proxy, but the
     * connection will be forced to use offline mode even when the proxy is running in online mode.
     *
     * @return the result
     */
    public static PreLoginComponentResult forceOfflineMode() {
      return FORCE_OFFLINEMODE;
    }

    /**
     * Denies the login with the specified reason.
     *
     * @param reason the reason for disallowing the connection
     * @return a new result
     */
    public static PreLoginComponentResult denied(final Component reason) {
      Preconditions.checkNotNull(reason, "reason");
      return new PreLoginComponentResult(Result.DISALLOWED, reason);
    }

    private enum Result {

    /**
     * The connection is allowed without any modifications to the proxy’s default mode.
     */
    ALLOWED,

    /**
     * The connection is allowed, and the proxy will enforce online mode for this connection.
     */
    FORCE_ONLINE,

    /**
     * The connection is allowed, and the proxy will enforce offline mode for this connection.
     */
    FORCE_OFFLINE,

    /**
     * The connection is denied and will be disconnected.
     */
    DISALLOWED
    }
  }
}
