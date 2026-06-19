/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.config.migration;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.velocitypowered.proxy.config.migration.ConfigurationMigration;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Logger;

/**
 * Migrates the removed {@code sample-players-in-ping} option into the {@code motd-hover}
 * {@code {players}} placeholder. When the option was enabled, the server list ping used to display
 * a sample of online players, so a {@code {players}} line is inserted at the top of
 * {@code motd-hover} to preserve that behavior. The legacy option is then removed.
 */
public final class CtdMotdHoverMigration implements ConfigurationMigration {

  private static final String LEGACY_KEY = "sample-players-in-ping";
  private static final String MOTD_HOVER_KEY = "motd-hover";
  private static final String PLAYERS_LINE = "{players}";

  @Override
  public boolean shouldMigrate(CommentedFileConfig config) {
    return config.contains(LEGACY_KEY);
  }

  @Override
  public void migrate(CommentedFileConfig config, Logger logger) {
    if (config.getOrElse(LEGACY_KEY, false)) {
      List<String> motdHover = new ArrayList<>();
      if (config.get(MOTD_HOVER_KEY) instanceof List<?> existing) {
        for (Object line : existing) {
          motdHover.add(String.valueOf(line));
        }
      }
      motdHover.addFirst(PLAYERS_LINE);
      config.set(MOTD_HOVER_KEY, motdHover);
    }

    config.remove(LEGACY_KEY);
  }
}
