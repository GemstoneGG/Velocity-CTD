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
import com.velocitypowered.api.proxy.Player;
import java.util.Arrays;
import net.kyori.adventure.key.Key;

/**
 * This event is fired when a cookie should be stored on a player's client. This process can be
 * initiated either by a proxy plugin or by a backend server. Velocity will wait on this event
 * to finish firing before discarding the cookie (if handled) or forwarding it to the client so
 * that it can store the cookie.
 */
@AwaitingEvent
public final class CookieStoreEvent implements ResultedEvent<CookieStoreEvent.ForwardResult> {

  /**
   * The player who should store the cookie.
   */
  private final Player player;

  /**
   * The original key identifying the cookie to be stored.
   */
  private final Key originalKey;

  /**
   * The original data payload of the cookie.
   */
  private final byte[] originalData;

  /**
   * The result indicating how the cookie should be handled by the proxy.
   */
  private ForwardResult result;

  /**
   * Creates a new instance.
   *
   * @param player the player who should store the cookie
   * @param key the identifier of the cookie
   * @param data the data of the cookie
   */
  public CookieStoreEvent(Player player, Key key, byte[] data) {
    this.player = Preconditions.checkNotNull(player, "player");
    this.originalKey = Preconditions.checkNotNull(key, "key");
    this.originalData = Preconditions.checkNotNull(data, "data");
    this.result = ForwardResult.forward();
  }

  @Override
  public ForwardResult getResult() {
    return result;
  }

  @Override
  public void setResult(ForwardResult result) {
    this.result = Preconditions.checkNotNull(result, "result");
  }

  /**
   * Returns the player who should store the cookie.
   *
   * @return the player
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Returns the original key identifying the cookie to be stored.
   *
   * @return the cookie key
   */
  public Key getOriginalKey() {
    return originalKey;
  }

  /**
   * Returns the original data of the cookie to be stored.
   *
   * @return the cookie data
   */
  public byte[] getOriginalData() {
    return originalData;
  }

  @Override
  public String toString() {
    return "CookieStoreEvent{"
        + ", originalKey=" + originalKey
        + ", originalData=" + Arrays.toString(originalData)
        + ", result=" + result
        + '}';
  }

  /**
   * A result determining whether to forward the cookie on.
   */
  public static final class ForwardResult implements Result {

    /**
     * A result indicating the cookie should be forwarded to the client unchanged.
     */
    private static final ForwardResult ALLOWED = new ForwardResult(true, null, null);

    /**
     * A result indicating the cookie has been handled by the proxy and should not be forwarded.
     */
    private static final ForwardResult DENIED = new ForwardResult(false, null, null);

    /**
     * Whether the cookie should be forwarded to the client.
     */
    private final boolean status;

    /**
     * A replacement key to use when forwarding the cookie, or {@code null} to use the original key.
     */
    private final Key key;

    /**
     * A replacement payload to use when forwarding the cookie, or {@code null} to use the original data.
     */
    private final byte[] data;

    private ForwardResult(boolean status, Key key, byte[] data) {
      this.status = status;
      this.key = key;
      this.data = data;
    }

    @Override
    public boolean isAllowed() {
      return status;
    }

    /**
     * Returns the replacement key to use when forwarding the cookie,
     * or {@code null} if the original key should be used.
     *
     * @return the new cookie key, or {@code null} if unchanged
     */
    public Key getKey() {
      return key;
    }

    /**
     * Returns the replacement data to use when forwarding the cookie,
     * or {@code null} if the original data should be used.
     *
     * @return the new cookie data, or {@code null} if unchanged
     */
    public byte[] getData() {
      return data;
    }

    @Override
    public String toString() {
      return status ? "forward to client" : "handled by proxy";
    }

    /**
     * Allows the cookie to be forwarded to the client so that it can store it.
     *
     * @return the forward result
     */
    public static ForwardResult forward() {
      return ALLOWED;
    }

    /**
     * Prevents the cookie from being forwarded to the client, the cookie is handled by the proxy.
     *
     * @return the handled result
     */
    public static ForwardResult handled() {
      return DENIED;
    }

    /**
     * Allows the cookie to be forwarded to the client so that it can store it, but silently
     * replaces the identifier of the cookie with another.
     *
     * @param key the identifier to use instead
     * @return a result with a new key
     */
    public static ForwardResult key(Key key) {
      Preconditions.checkNotNull(key, "key");
      return new ForwardResult(true, key, null);
    }

    /**
     * Allows the cookie to be forwarded to the client so that it can store it, but silently
     * replaces the data of the cookie with another.
     *
     * @param data the data of the cookie to use instead
     * @return a result with new data
     */
    public static ForwardResult data(byte[] data) {
      Preconditions.checkNotNull(data, "data");
      return new ForwardResult(true, null, data);
    }
  }
}
