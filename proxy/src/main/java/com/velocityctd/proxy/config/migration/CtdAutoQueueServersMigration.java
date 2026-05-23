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

import static java.util.stream.Collectors.toMap;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.velocitypowered.proxy.config.migration.ConfigurationMigration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.Logger;

public final class CtdAutoQueueServersMigration implements ConfigurationMigration {

  private static final String SECTION_KEY = "queue.auto-queue-servers";

  private static final Map<String, Object> SECTION_VALUES = Stream.of(
      Map.entry("minigames-limbo", List.of("minigames1", "minigames2")),
      Map.entry("factions-limbo", "factions")
  ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new));

  private static final String SECTION_COMMENT = ""
      + " The list of servers that should auto-queue players on join.\n"
      + " When a player joins the server on the left, it will enqueue them to the server(s) to the right.\n"
      + " If allow-multi-queue is disabled and multiple servers are configured, only the first server will be chosen.";

  @Override
  public boolean shouldMigrate(CommentedFileConfig config) {
    return !config.contains(SECTION_KEY);
  }

  @Override
  public void migrate(CommentedFileConfig config, Logger logger) {
    boolean commented = false;
    for (Map.Entry<String, Object> entry : SECTION_VALUES.entrySet()) {
      String key = SECTION_KEY + "." + entry.getKey();
      Object value = entry.getValue();

      config.set(key, value);

      if (!commented) {
        config.setComment(key, SECTION_COMMENT);
        commented = true;
      }
    }
  }
}
