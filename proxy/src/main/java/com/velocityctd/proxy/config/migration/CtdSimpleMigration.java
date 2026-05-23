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
import org.apache.logging.log4j.Logger;

/**
 * The base class for a basic Velocity-CTD migration. A Velocity-CTD migration doesn't
 * check or update {@link ConfigurationMigration#configVersion(CommentedFileConfig)},
 * instead it simply checks if the key is present or not, adding the key (along with its
 * default value and comment) if not. This leaves the {@code config-version} key in the
 * hands of upstream Velocity.
 */
public class CtdSimpleMigration implements ConfigurationMigration {

  private final String optionKey;
  private final Object optionDefaultValue;
  private final String optionComment;

  protected CtdSimpleMigration(String optionKey, Object optionDefaultValue, String optionComment) {
    this.optionKey = optionKey;
    this.optionDefaultValue = optionDefaultValue;
    this.optionComment = optionComment;
  }

  @Override
  public boolean shouldMigrate(CommentedFileConfig config) {
    return !config.contains(optionKey);
  }

  @Override
  public void migrate(CommentedFileConfig config, Logger logger) {
    config.set(optionKey, optionDefaultValue);

    if (optionComment != null) {
      config.setComment(optionKey, optionComment);
    }
  }
}
