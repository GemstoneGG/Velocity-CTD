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

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.velocitypowered.proxy.config.migration.ConfigurationMigration;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Documents the per-forced-host {@code motd}, {@code motd-hover} and {@code server-icon} options
 * in existing configurations. The {@code [forced-hosts]} documentation lives in the comment of the
 * first forced host, so the new paragraph is appended to that comment when it still documents the
 * table format without mentioning the new options.
 */
public final class CtdForcedHostPingMigration implements ConfigurationMigration {

  private static final String FORCED_HOSTS_KEY = "forced-hosts";
  private static final String TABLE_FORMAT_MARKER = "Table format - allows setting per-host options:";
  private static final String NEW_OPTION_MARKER = "server-icon";

  private static final String PING_OVERRIDES_COMMENT = ""
      + " The table format can also override the server list ping for players who added this host.\n"
      + " \"motd\" and \"motd-hover\" work like the global options, and \"server-icon\" is a path to a 64x64 PNG.\n"
      + " Any of them may be omitted to keep the global \"motd\", \"motd-hover\" or \"server-icon.png\":\n"
      + "   \"lobby.example.com\" = { servers = [\"lobby\"], motd = [\"<#ff3a4c>Lobby\"], motd-hover = [\"{players}\"],"
      + " server-icon = \"lobby-icon.png\" }\n";

  @Override
  public boolean shouldMigrate(CommentedFileConfig config) {
    return findDocumentedEntry(config) != null;
  }

  @Override
  public void migrate(CommentedFileConfig config, Logger logger) {
    CommentedConfig.Entry entry = findDocumentedEntry(config);
    if (entry == null) {
      return;
    }

    config.setComment(List.of(FORCED_HOSTS_KEY, entry.getKey()),
        entry.getComment().stripTrailing() + "\n\n" + PING_OVERRIDES_COMMENT);
  }

  /**
   * Finds the forced host carrying the table format documentation, if that documentation does not
   * mention the ping overrides yet.
   */
  @Nullable
  private static CommentedConfig.Entry findDocumentedEntry(CommentedFileConfig config) {
    if (!(config.get(FORCED_HOSTS_KEY) instanceof CommentedConfig forcedHosts)) {
      return null;
    }

    for (CommentedConfig.Entry entry : forcedHosts.entrySet()) {
      String comment = entry.getComment();
      if (comment != null && comment.contains(TABLE_FORMAT_MARKER) && !comment.contains(NEW_OPTION_MARKER)) {
        return entry;
      }
    }

    return null;
  }
}
