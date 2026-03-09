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

package com.velocitypowered.proxy.config.migration;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import org.apache.logging.log4j.Logger;

/**
 * Configuration Migration interface.
 */
public sealed interface ConfigurationMigration permits
        ForwardingMigration,
        KeyAuthenticationMigration,
        MiniMessageTranslationsMigration,
        MotdMigration,
        TransferIntegrationMigration {

  /**
   * Determines whether this migration should be applied to the given config.
   *
   * @param config the config file to evaluate
   * @return {@code true} if the migration is applicable; {@code false} otherwise
   */
  boolean shouldMigrate(CommentedFileConfig config);

  /**
   * Applies this migration to the given config.
   *
   * @param config the config file to modify
   * @param logger the logger for emitting warnings or progress messages
   * @throws IOException if an error occurs while applying the migration
   */
  void migrate(CommentedFileConfig config, Logger logger) throws IOException;

  /**
   * Gets the configuration version.
   *
   * @param config the configuration.
   * @return configuration version
   */
  default double configVersion(CommentedFileConfig config) {
    final String stringVersion = config.getOrElse("config-version", "1.0");
    try {
      return Double.parseDouble(stringVersion);
    } catch (Exception e) {
      return 1.0;
    }
  }
}
