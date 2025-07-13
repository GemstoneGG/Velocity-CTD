/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.util;

/**
 * Represents where a chat message is going to be sent.
 */
public enum MessagePosition {

  /**
   * The chat message will appear in the client's HUD.
   * The client can filter out these messages.
   */
  CHAT,

  /**
   * The chat message will appear in the client's HUD and can't be dismissed.
   */
  SYSTEM,

  /**
   * The chat message will appear above the player's main HUD.
   * This text format supports few component features, such as hover events.
   */
  ACTION_BAR
}
