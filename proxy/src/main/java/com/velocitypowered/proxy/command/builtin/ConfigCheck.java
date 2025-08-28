/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

package com.velocitypowered.proxy.command.builtin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.ConfigDetector;
import com.velocitypowered.proxy.config.ConfigDetector.ConfigAnalysis;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Implements the {@code /velocity configcheck} command.
 */
public final class ConfigCheck implements Command<CommandSource> {

  private static final Logger logger = LogManager.getLogger(ConfigCheck.class);
  private final VelocityServer server;

  public ConfigCheck(VelocityServer server) {
    this.server = server;
  }

  @Override
  public int run(CommandContext<CommandSource> context) {
    final CommandSource source = context.getSource();
    
    // Get the default config path
    Path configPath = Path.of("velocity.toml");
    
    try {
      ConfigDetector detector = new ConfigDetector(logger);
      ConfigAnalysis analysis = detector.analyzeConfiguration(configPath);
      
      // Send formatted results to the command source
      source.sendMessage(Component.text("=== Configuration Analysis ===", NamedTextColor.GOLD));
      
      if (!analysis.isOutdated()) {
        source.sendMessage(Component.text("✓ Configuration is up to date (version " + 
            analysis.getCurrentVersion() + ")", NamedTextColor.GREEN));
      } else {
        source.sendMessage(Component.text("⚠ Configuration needs updates:", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("  Current version: " + analysis.getCurrentVersion(), NamedTextColor.GRAY));
        source.sendMessage(Component.text("  Latest version: " + analysis.getLatestVersion(), NamedTextColor.GRAY));
        
        if (!analysis.getMissingOptions().isEmpty()) {
          source.sendMessage(Component.text("  Missing options:", NamedTextColor.RED));
          for (String option : analysis.getMissingOptions()) {
            source.sendMessage(Component.text("    - " + option, NamedTextColor.RED));
          }
        }
        
        if (!analysis.getDeprecatedOptions().isEmpty()) {
          source.sendMessage(Component.text("  Deprecated options:", NamedTextColor.YELLOW));
          for (String option : analysis.getDeprecatedOptions()) {
            source.sendMessage(Component.text("    - " + option, NamedTextColor.YELLOW));
          }
        }
        
        source.sendMessage(Component.text("  Recommendations:", NamedTextColor.GOLD));
        for (String recommendation : analysis.getRecommendations()) {
          source.sendMessage(Component.text("    - " + recommendation, NamedTextColor.WHITE));
        }
      }
      
    } catch (IOException e) {
      source.sendMessage(Component.text("Error analyzing configuration: " + e.getMessage(), NamedTextColor.RED));
      logger.error("Failed to analyze configuration file: {}", configPath, e);
    }
    
    return Command.SINGLE_SUCCESS;
  }
}
