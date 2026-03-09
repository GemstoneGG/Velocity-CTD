/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.proxy;

/**
 * This event is fired when the proxy is reloaded by the user using {@code /velocity reload}.
 */
public class ProxyReloadEvent {

  /**
   * Creates a new {@code ProxyReloadEvent}.
   */
  public ProxyReloadEvent() {
  }

  /**
   * Returns a string representation of this {@code ProxyReloadEvent}.
   *
   * <p>As this event has no fields, the output is a fixed string identifier.</p>
   *
   * @return the string {@code "ProxyReloadEvent"}
   */
  @Override
  public String toString() {
    return "ProxyReloadEvent";
  }
}
