/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.queue;

import java.util.Locale;

/**
 * The strategy a server group uses to pick a backend for an individual player.
 *
 * <p>Strategy names are configured in lower snake case. Hyphens are accepted as an
 * alternative to underscores when reading configuration values.</p>
 */
public enum SelectionStrategy {

  /**
   * Send the player to the available backend with the fewest players. Ties are broken
   * by the order in which the backends are listed in the configuration. Counts are a
   * best-effort snapshot, so the choice is advisory rather than a hard capacity guarantee.
   */
  LEAST_PLAYERS("least_players"),

  /**
   * Send the player back to the backend they last played on within this group when it
   * is available, otherwise fall back to {@link #LEAST_PLAYERS}. Last-server values are
   * group-scoped and are recorded only after a successful connection.
   */
  LAST_SERVER("last_server"),

  /**
   * Rotate across the available backends in their configured order.
   */
  ROUND_ROBIN("round_robin"),

  /**
   * Choose a random available backend.
   */
  RANDOM("random");

  private final String configName;

  SelectionStrategy(String configName) {
    this.configName = configName;
  }

  /**
   * Returns the name used for this strategy in the configuration file.
   *
   * @return the lower snake case configuration name
   */
  public String configName() {
    return configName;
  }

  /**
   * Parses a configured strategy name.
   *
   * <p>The comparison is case-insensitive and treats hyphens as underscores. A missing,
   * blank, or unrecognised value selects {@link #LEAST_PLAYERS}.</p>
   *
   * @param value the configured value, which may be {@code null}
   * @return the matching strategy, or {@link #LEAST_PLAYERS} when no match exists
   */
  public static SelectionStrategy fromConfig(String value) {
    if (value == null || value.isBlank()) {
      return LEAST_PLAYERS;
    }

    String normalized = value.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    for (SelectionStrategy strategy : values()) {
      if (strategy.configName.equals(normalized)) {
        return strategy;
      }
    }

    return LEAST_PLAYERS;
  }
}
