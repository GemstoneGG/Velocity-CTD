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

package com.velocitypowered.proxy.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Detects outdated configuration files by comparing them to the default embedded configuration.
 * This class provides detailed analysis of configuration differences and missing options.
 *
 * @param logger the logger used to output configuration analysis results
 */
public record ConfigDetector(Logger logger) {

  /**
   * Path to the embedded default Velocity configuration resource.
   */
  private static final String DEFAULT_CONFIG_RESOURCE = "default-velocity.toml";

  /**
   * Configuration sections that are ignored during analysis.
   *
   * <p>These sections are user-specific and should not be flagged
   * as missing or deprecated when comparing configurations.</p>
   */
  private static final Set<String> IGNORED_SECTIONS = Set.of("servers", "server-links",
          "forced-hosts", "slash-servers", "playercaps", "proxy-addresses",
          "command-aliases", "proxy-command-aliases", "auto-queue-servers");

  /**
   * Configuration analysis result containing details about outdated configurations.
   *
   * @param isOutdated        whether the configuration is outdated
   * @param currentVersion    the current configuration version
   * @param latestVersion     the latest available configuration version
   * @param missingOptions    list of missing configuration options
   * @param deprecatedOptions list of deprecated configuration options
   * @param recommendations   list of recommendations for configuration improvements
   */
  public record ConfigAnalysis(boolean isOutdated, String currentVersion, String latestVersion,
                               List<String> missingOptions, List<String> deprecatedOptions,
                               List<String> recommendations) {

    @Override
    public @NotNull String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Configuration Analysis:\n");
      sb.append("  Current Version: ").append(currentVersion).append("\n");
      sb.append("  Latest Version: ").append(latestVersion).append("\n");
      sb.append("  Is Outdated: ").append(isOutdated).append("\n");

      if (!missingOptions.isEmpty()) {
        sb.append("  Missing Options:\n");
        for (String option : missingOptions) {
          sb.append("    - ").append(option).append("\n");
        }
      }

      if (!deprecatedOptions.isEmpty()) {
        sb.append("  Deprecated Options:\n");
        for (String option : deprecatedOptions) {
          sb.append("    - ").append(option).append("\n");
        }
      }

      if (!recommendations.isEmpty()) {
        sb.append("  Recommendations:\n");
        for (String rec : recommendations) {
          sb.append("    - ").append(rec).append("\n");
        }
      }

      return sb.toString();
    }
  }

  /**
   * Analyzes the configuration file for outdated options and missing configurations.
   *
   * @param configPath the path to the configuration file to analyze
   * @return a ConfigAnalysis object containing the analysis results
   * @throws IOException if there's an error reading the configuration files
   */
  public ConfigAnalysis analyzeConfiguration(Path configPath) throws IOException {
    CommentedConfig defaultConfig = loadDefaultConfig();
    if (defaultConfig == null) {
      throw new IOException("Could not load default configuration from resources");
    }

    CommentedFileConfig currentConfig = CommentedFileConfig.builder(configPath)
        .preserveInsertionOrder()
        .sync()
        .build();
    currentConfig.load();

    CommentedConfig analysisConfig = CommentedConfig.inMemory();
    
    for (CommentedConfig.Entry entry : currentConfig.entrySet()) {
      analysisConfig.set(entry.getKey(), entry.getValue());
    }
    
    for (CommentedConfig.Entry entry : defaultConfig.entrySet()) {
      if (!analysisConfig.contains(entry.getKey())) {
        analysisConfig.set(entry.getKey(), entry.getValue());
      }
    }

    String currentVersion = currentConfig.getOrElse("config-version", "1.0");
    String latestVersion = defaultConfig.getOrElse("config-version", "1.0");

    List<String> missingOptions = findMissingOptions(defaultConfig, analysisConfig);
    List<String> deprecatedOptions = findDeprecatedOptions(defaultConfig, analysisConfig);
    List<String> recommendations = generateRecommendations(currentVersion, latestVersion, missingOptions, deprecatedOptions);

    boolean isOutdated = !currentVersion.equals(latestVersion)
        || !missingOptions.isEmpty() || !deprecatedOptions.isEmpty();

    return new ConfigAnalysis(isOutdated, currentVersion, latestVersion,
        missingOptions, deprecatedOptions, recommendations);
  }

  /**
   * Loads the default embedded configuration from resources.
   *
   * @return the default configuration as a CommentedConfig
   * @throws IOException if there's an error reading the default configuration
   */
  private CommentedConfig loadDefaultConfig() throws IOException {
    URL defaultConfigUrl = VelocityConfiguration.class.getClassLoader()
        .getResource(DEFAULT_CONFIG_RESOURCE);
    if (defaultConfigUrl == null) {
      throw new IOException("Default configuration resource not found: " + DEFAULT_CONFIG_RESOURCE);
    }

    try (InputStream is = defaultConfigUrl.openStream()) {
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      TomlParser parser = new TomlParser();
      return parser.parse(content);
    }
  }

  /**
   * Finds options that exist in the default config but are missing from the current config.
   *
   * @param defaultConfig the default configuration
   * @param currentConfig the current configuration
   * @return list of missing option paths
   */
  private List<String> findMissingOptions(CommentedConfig defaultConfig, CommentedConfig currentConfig) {
    List<String> missingOptions = new ArrayList<>();
    findMissingOptionsRecursive(defaultConfig, currentConfig, "", missingOptions);
    return missingOptions;
  }

  /**
   * Recursively finds missing options by traversing the configuration tree.
   *
   * @param defaultConfig  the default configuration
   * @param currentConfig  the current configuration
   * @param currentPath    the current path being checked
   * @param missingOptions list to collect missing options
   */
  private void findMissingOptionsRecursive(CommentedConfig defaultConfig, CommentedConfig currentConfig,
                                           String currentPath, List<String> missingOptions) {
    for (CommentedConfig.Entry entry : defaultConfig.entrySet()) {
      String key = entry.getKey();
      String fullPath = currentPath.isEmpty() ? key : currentPath + "." + key;

      if (IGNORED_SECTIONS.contains(key)) {
        continue;
      }

      if (!currentConfig.contains(key)) {
        missingOptions.add(fullPath);
      } else {
        Object defaultValueObj = entry.getValue();
        Object currentValueObj = currentConfig.get(key);

        if (defaultValueObj instanceof CommentedConfig defaultValue
            && currentValueObj instanceof CommentedConfig currentValue) {
          findMissingOptionsRecursive(defaultValue, currentValue, fullPath, missingOptions);
        }
      }
    }
  }

  /**
   * Finds deprecated options that exist in the current config but not in the default config.
   *
   * @param defaultConfig the default configuration
   * @param currentConfig the current configuration
   * @return list of deprecated option paths
   */
  private List<String> findDeprecatedOptions(CommentedConfig defaultConfig, CommentedConfig currentConfig) {
    List<String> deprecatedOptions = new ArrayList<>();
    findDeprecatedOptionsRecursive(defaultConfig, currentConfig, "", deprecatedOptions);
    return deprecatedOptions;
  }

  /**
   * Recursively finds deprecated options by traversing the configuration tree.
   *
   * @param defaultConfig the default configuration
   * @param currentConfig the current configuration
   * @param currentPath the current path being checked
   * @param deprecatedOptions list to collect deprecated options
   */
  private void findDeprecatedOptionsRecursive(CommentedConfig defaultConfig, CommentedConfig currentConfig,
                                              String currentPath, List<String> deprecatedOptions) {
    for (CommentedConfig.Entry entry : currentConfig.entrySet()) {
      String key = entry.getKey();
      String fullPath = currentPath.isEmpty() ? key : currentPath + "." + key;

      if (IGNORED_SECTIONS.contains(key)) {
        continue;
      }

      if (!defaultConfig.contains(key)) {
        deprecatedOptions.add(fullPath);
      } else {
        Object defaultValueObj = defaultConfig.get(key);
        Object currentValueObj = entry.getValue();

        if (defaultValueObj instanceof CommentedConfig defaultValue
            && currentValueObj instanceof CommentedConfig currentValue) {
          findDeprecatedOptionsRecursive(defaultValue, currentValue, fullPath, deprecatedOptions);
        }
      }
    }
  }

  /**
   * Generates recommendations based on the analysis results.
   *
   * @param currentVersion the current config version
   * @param latestVersion the latest config version
   * @param missingOptions list of missing options
   * @param deprecatedOptions list of deprecated options
   * @return list of recommendations
   */
  private List<String> generateRecommendations(String currentVersion, String latestVersion,
                                               List<String> missingOptions, List<String> deprecatedOptions) {
    List<String> recommendations = new ArrayList<>();

    if (!currentVersion.equals(latestVersion)) {
      recommendations.add("Update config-version from " + currentVersion + " to " + latestVersion);
    }

    if (!missingOptions.isEmpty()) {
      recommendations.add("Add missing configuration options: " + String.join(", ", missingOptions));
    }

    if (!deprecatedOptions.isEmpty()) {
      recommendations.add("Remove deprecated configuration options: " + String.join(", ", deprecatedOptions));
    }

    if (recommendations.isEmpty()) {
      recommendations.add("Configuration is up to date");
    }

    return recommendations;
  }

  /**
   * Logs the configuration analysis results.
   *
   * @param analysis the configuration analysis results
   */
  public void logAnalysis(ConfigAnalysis analysis) {
    if (!analysis.isOutdated()) {
      logger.info("Configuration is up to date (version {})", analysis.currentVersion());
      return;
    }

    logger.warn("Configuration analysis detected outdated configuration:");
    logger.warn("  Current version: {}", analysis.currentVersion());
    logger.warn("  Latest version: {}", analysis.latestVersion());

    if (!analysis.missingOptions().isEmpty()) {
      logger.warn("  Missing options: {}", String.join(", ", analysis.missingOptions()));
    }

    if (!analysis.deprecatedOptions().isEmpty()) {
      logger.warn("  Deprecated options: {}", String.join(", ", analysis.deprecatedOptions()));
    }

    logger.warn("  Recommendations:");
    for (String recommendation : analysis.recommendations()) {
      logger.warn("    - {}", recommendation);
    }
  }

  /**
   * Checks if a configuration file needs to be updated and logs the results.
   *
   * @param configPath the path to the configuration file
   * @return true if the configuration is outdated, false otherwise
   */
  public boolean checkAndLogConfiguration(Path configPath) {
    try {
      ConfigAnalysis analysis = analyzeConfiguration(configPath);
      logAnalysis(analysis);
      return analysis.isOutdated();
    } catch (IOException e) {
      logger.error("Failed to analyze configuration file: {}", configPath, e);
      return false;
    }
  }
}
