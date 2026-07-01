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

package com.velocityctd.proxy.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.velocitypowered.api.util.ProxyVersion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Compares the running build of Velocity-CTD against the latest release on GitHub.
 *
 * <p>The comparison is performed by resolving the {@code -b<build>} suffix of the proxy version
 * against the latest {@code build-<n>} GitHub release. This is used both by {@code /velocity info}
 * and by the non-blocking startup version check.</p>
 */
public final class VersionChecker {

  private static final Logger LOGGER = LogManager.getLogger(VersionChecker.class);

  /**
   * Version distance constant indicating the current version is up to date with GitHub.
   */
  public static final int DISTANCE_LATEST = 0;

  /**
   * Version distance constant indicating an error occurred during GitHub comparison.
   */
  public static final int DISTANCE_ERROR = -1;

  /**
   * Version distance constant indicating the build number could not be resolved.
   */
  public static final int DISTANCE_UNKNOWN = -2;

  private static final Pattern BUILD_NUMBER = Pattern.compile("-b(\\d+)$");

  private static final Pattern RELEASE_TAG = Pattern.compile("^build-(\\d+)$");

  private static final Gson VERSION_GSON = new Gson();

  private VersionChecker() {
  }

  /**
   * Determines how many releases the given proxy version is behind the latest GitHub release.
   *
   * @param version the proxy version string (expected to contain a {@code -b<build>} suffix)
   * @return the number of releases behind, or one of {@link #DISTANCE_LATEST}, {@link #DISTANCE_ERROR} or {@link #DISTANCE_UNKNOWN}
   */
  public static int fetchDistanceFromGitHub(String version) {
    Matcher matcher = BUILD_NUMBER.matcher(version);
    if (!matcher.find()) {
      return DISTANCE_UNKNOWN;
    }

    int currentBuild = Integer.parseInt(matcher.group(1));
    try {
      HttpURLConnection connection = (HttpURLConnection) URI.create("https://api.github.com/repos/GemstoneGG/Velocity-CTD/releases/latest").toURL().openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setRequestProperty("User-Agent", "Velocity-CTD/" + version + " (+https://github.com/GemstoneGG/Velocity-CTD)");
      connection.setRequestProperty("Accept", "application/vnd.github+json");
      connection.connect();
      if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
        return DISTANCE_UNKNOWN; // No releases published
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        JsonObject obj = VERSION_GSON.fromJson(reader, JsonObject.class);
        Matcher tagMatcher = RELEASE_TAG.matcher(obj.get("tag_name").getAsString());
        if (!tagMatcher.matches()) {
          return DISTANCE_ERROR;
        }

        int latestBuild = Integer.parseInt(tagMatcher.group(1));
        return Math.max(latestBuild - currentBuild, DISTANCE_LATEST);
      } catch (JsonSyntaxException | NumberFormatException e) {
        LOGGER.error("Error parsing latest-release response from GitHub", e);
        return DISTANCE_ERROR;
      }
    } catch (IOException e) {
      LOGGER.error("Error contacting GitHub for version comparison", e);
      return DISTANCE_ERROR;
    }
  }

  /**
   * Performs a non-blocking version check after the proxy has fully started.
   *
   * <p>The check runs on a daemon thread so it neither delays startup nor keeps the JVM alive on
   * shutdown. Any failure is logged and never propagated, so it cannot bring down the proxy.</p>
   *
   * <p>Snapshot (development) builds are not checked against GitHub; instead a notice is logged.</p>
   *
   * @param version the running proxy version
   */
  public static void checkOnStartup(ProxyVersion version) {
    Thread thread = new Thread(() -> {
      try {
        logVersionStatus(version);
      } catch (Throwable t) {
        LOGGER.warn("Failed to perform startup version check", t);
      }
    }, "Velocity-CTD Version Check");
    thread.setDaemon(true);
    thread.start();
  }

  private static void logVersionStatus(ProxyVersion version) {
    if (version.isDevelopmentVersion()) {
      LOGGER.info("You are running a development build of Velocity-CTD.");
      return;
    }

    int dist = fetchDistanceFromGitHub(version.getVersion());
    switch (dist) {
      case DISTANCE_ERROR -> LOGGER.warn("There was an error when attempting to fetch Velocity-CTD's version information.");
      case DISTANCE_UNKNOWN -> LOGGER.warn("Unable to fetch Velocity-CTD's version information.");
      case DISTANCE_LATEST -> LOGGER.info("You are running the latest version of Velocity-CTD.");
      default -> LOGGER.warn("You are {} release(s) behind. Consider updating Velocity-CTD.", dist);
    }
  }
}
