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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Detects outdated configuration files by comparing them to the default embedded configuration.
 * This class provides detailed analysis of configuration differences and missing options.
 *
 * <p>This implementation includes caching to avoid expensive repeated parsing and tree traversal.
 * Cached results are invalidated based on file modification times.</p>
 */
public class ConfigDetector {

  private static final String DEFAULT_CONFIG_RESOURCE = "default-velocity.toml";

  private static final Set<String> IGNORED_SECTIONS = Set.of("servers", "server-links",
          "forced-hosts", "slash-servers", "playercaps", "proxy-addresses",
          "command-aliases", "proxy-command-aliases", "auto-queue-servers");

  private final Logger logger;

  // Cache for parsed default config (static, shared)
  private static volatile CommentedConfig cachedDefaultConfig = null;
  private static volatile long defaultConfigCacheTimestamp = 0;
  private static final long DEFAULT_CONFIG_TTL_MS = 60_000; // 1 minute

  // Analysis cache per config file
  private static final ConcurrentMap<Path, AnalysisCacheEntry> ANALYSIS_CACHE = new ConcurrentHashMap<>();
  private static final long ANALYSIS_CACHE_TTL_MS = 10_000; // 10 seconds

  /**
   * Creates a new ConfigDetector with the specified logger.
   *
   * @param logger the logger to use for configuration analysis output
   */
  public ConfigDetector(Logger logger) {
    this.logger = logger;
  }

  /**
   * Configuration analysis result.
   */
  public static final class ConfigAnalysis {
    private final boolean isOutdated;
    private final String currentVersion;
    private final String latestVersion;
    private final List<String> missingOptions;
    private final List<String> deprecatedOptions;
    private final List<String> recommendations;

    public ConfigAnalysis(boolean isOutdated, String currentVersion, String latestVersion,
        List<String> missingOptions, List<String> deprecatedOptions, List<String> recommendations) {
      this.isOutdated = isOutdated;
      this.currentVersion = currentVersion;
      this.latestVersion = latestVersion;
      this.missingOptions = List.copyOf(missingOptions);
      this.deprecatedOptions = List.copyOf(deprecatedOptions);
      this.recommendations = List.copyOf(recommendations);
    }

    public boolean isOutdated() { return isOutdated; }
    public String currentVersion() { return currentVersion; }
    public String latestVersion() { return latestVersion; }
    public List<String> missingOptions() { return missingOptions; }
    public List<String> deprecatedOptions() { return deprecatedOptions; }
    public List<String> recommendations() { return recommendations; }

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
        for (String rec : recommendations()) {
          sb.append("    - ").append(rec).append("\n");
        }
      }

      return sb.toString();
    }
  }

  /**
   * Cache entry for analysis results.
   */
  private static final class AnalysisCacheEntry {
    final long lastModified;
    final ConfigAnalysis analysis;
    final long cachedAt;

    AnalysisCacheEntry(long lastModified, ConfigAnalysis analysis) {
      this.lastModified = lastModified;
      this.analysis = analysis;
      this.cachedAt = System.currentTimeMillis();
    }

    boolean isExpired() {
      return System.currentTimeMillis() - cachedAt > ANALYSIS_CACHE_TTL_MS;
    }
  }

  public ConfigAnalysis analyzeConfiguration(final Path configPath) throws IOException {
    // Check cache first
    long fileLastModified = Files.getLastModifiedTime(configPath).toMillis();
    AnalysisCacheEntry cachedEntry = ANALYSIS_CACHE.get(configPath);
    if (cachedEntry != null
        && cachedEntry.lastModified == fileLastModified
        && !cachedEntry.isExpired()) {
      return cachedEntry.analysis;
    }

    // Load or get cached default config
    CommentedConfig defaultConfig = getCachedDefaultConfig();
    if (defaultConfig == null) {
      throw new IOException("Could not load default configuration from resources");
    }

    // Load current config
    CommentedFileConfig currentConfig = CommentedFileConfig.builder(configPath)
        .preserveInsertionOrder()
        .sync()
        .build();
    currentConfig.load();

    // Create merged analysis config
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
    List<String> recommendations = generateRecommendations(currentVersion, latestVersion,
        missingOptions, deprecatedOptions);

    boolean isOutdated = !currentVersion.equals(latestVersion)
        || !missingOptions.isEmpty() || !deprecatedOptions.isEmpty();

    ConfigAnalysis analysis = new ConfigAnalysis(isOutdated, currentVersion, latestVersion,
        missingOptions, deprecatedOptions, recommendations);

    // Cache the result
    ANALYSIS_CACHE.put(configPath, new AnalysisCacheEntry(fileLastModified, analysis));

    return analysis;
  }

  private CommentedConfig getCachedDefaultConfig() throws IOException {
    long now = System.currentTimeMillis();
    if (cachedDefaultConfig != null && now - defaultConfigCacheTimestamp < DEFAULT_CONFIG_TTL_MS) {
      return cachedDefaultConfig;
    }

    // Reload default config
    URL defaultConfigUrl = ConfigDetector.class.getClassLoader()
        .getResource(DEFAULT_CONFIG_RESOURCE);
    if (defaultConfigUrl == null) {
      throw new IOException("Default configuration resource not found: " + DEFAULT_CONFIG_RESOURCE);
    }

    try (InputStream is = defaultConfigUrl.openStream()) {
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      TomlParser parser = new TomlParser();
      cachedDefaultConfig = parser.parse(content);
      defaultConfigCacheTimestamp = now;
      return cachedDefaultConfig;
    }
  }

  private List<String> findMissingOptions(final CommentedConfig defaultConfig, final CommentedConfig currentConfig) {
    List<String> missingOptions = new ArrayList<>();
    findMissingOptionsRecursive(defaultConfig, currentConfig, "", missingOptions);
    return missingOptions;
  }

  private void findMissingOptionsRecursive(final CommentedConfig defaultConfig, final CommentedConfig currentConfig,
                                           final String currentPath, final List<String> missingOptions) {
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

  private List<String> findDeprecatedOptions(final CommentedConfig defaultConfig, final CommentedConfig currentConfig) {
    List<String> deprecatedOptions = new ArrayList<>();
    findDeprecatedOptionsRecursive(defaultConfig, currentConfig, "", deprecatedOptions);
    return deprecatedOptions;
  }

  private void findDeprecatedOptionsRecursive(final CommentedConfig defaultConfig, final CommentedConfig currentConfig,
                                               final String currentPath, final List<String> deprecatedOptions) {
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

  private List<String> generateRecommendations(final String currentVersion, final String latestVersion,
                                               final List<String> missingOptions, final List<String> deprecatedOptions) {
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

  public void logAnalysis(final ConfigAnalysis analysis) {
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

  public boolean checkAndLogConfiguration(final Path configPath) {
    try {
      ConfigAnalysis analysis = analyzeConfiguration(configPath);
      logAnalysis(analysis);
      return analysis.isOutdated();
    } catch (IOException e) {
      logger.error("Failed to analyze configuration file: {}", configPath, e);
      return false;
    }
  }

  /**
   * Clears the default configuration cache. Useful for testing.
   */
  public static void clearDefaultConfigCache() {
    cachedDefaultConfig = null;
    defaultConfigCacheTimestamp = 0;
  }

  /**
   * Clears the analysis cache for a specific config file.
   *
   * @param configPath the config path to clear, or null to clear all
   */
  public void clearAnalysisCache(Path configPath) {
    if (configPath == null) {
      ANALYSIS_CACHE.clear();
    } else {
      ANALYSIS_CACHE.remove(configPath);
    }
  }
}